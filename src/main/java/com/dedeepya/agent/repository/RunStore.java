package com.dedeepya.agent.repository;

import com.dedeepya.agent.config.AgentProperties;
import com.dedeepya.agent.dto.RunMode;
import com.dedeepya.agent.dto.request.DecisionRequest;
import com.dedeepya.agent.dto.request.RunRequest;
import com.dedeepya.agent.dto.response.CreditReceiptResponse;
import com.dedeepya.agent.dto.response.PendingApprovalResponse;
import com.dedeepya.agent.dto.response.RunResponse;
import com.dedeepya.agent.dto.response.TokenUsageResponse;
import com.dedeepya.agent.engine.*;
import com.dedeepya.agent.exception.*;
import com.dedeepya.agent.security.Actor;
import com.dedeepya.agent.tools.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class RunStore {
  public record Run(
      UUID id,
      UUID sessionId,
      String tenant,
      String requester,
      String mode,
      String status,
      RunState state,
      String errorCode,
      Instant createdAt,
      Instant deadline) {}

  public record Started(Run run, boolean created) {}

  private record Session(UUID id, String tenant, String owner, String transcript, UUID activeRun) {}

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final ContextPolicy context;
  private final AgentProperties config;

  public RunStore(
      JdbcTemplate jdbc, TransactionTemplate tx, ContextPolicy context, AgentProperties config) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.context = context;
    this.config = config;
  }

  public UUID createSession(Actor actor) {
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update(
        "INSERT INTO sessions(id,tenant,owner_id,transcript,created_at,updated_at)"
            + " VALUES(?,?,?,'[]',?,?)",
        id,
        actor.tenant(),
        actor.subject(),
        Timestamp.from(now),
        Timestamp.from(now));
    return id;
  }

  public Started begin(UUID sessionId, Actor actor, String key, RunRequest request) {
    if (key == null || !key.matches("[a-zA-Z0-9._:-]{8,100}"))
      throw ApiException.bad(
          "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 8–100 safe characters");
    String hash = sha256(Jsons.write(request));
    return tx.execute(
        status -> {
          Session session = ownedSession(sessionId, actor, true);
          var existing =
              jdbc.query(
                  "SELECT * FROM runs WHERE session_id=? AND idempotency_key=?",
                  this::mapRun,
                  sessionId,
                  key);
          if (!existing.isEmpty()) {
            String oldHash =
                jdbc.queryForObject(
                    "SELECT request_hash FROM runs WHERE id=?", String.class, existing.get(0).id());
            if (!hash.equals(oldHash))
              throw ApiException.conflict(
                  "IDEMPOTENCY_CONFLICT", "Key was already used for a different request");
            return new Started(existing.get(0), false);
          }
          if (session.activeRun() != null)
            throw ApiException.conflict("SESSION_BUSY", "This session already has an active run");
          RunState state = new RunState();
          state.transcript = Jsons.transcript(session.transcript());
          state.model =
              request.mode() == RunMode.ORDER_STATUS ? config.economyModel() : config.model();
          state.orderId = request.orderId();
          context.addUser(state, request);
          UUID id = UUID.randomUUID();
          Instant now = Instant.now(), deadline = now.plus(config.runTimeout());
          jdbc.update(
              "INSERT INTO"
                  + " runs(id,session_id,tenant,requester,idempotency_key,request_hash,mode,status,state_json,created_at,deadline)"
                  + " VALUES(?,?,?,?,?,?,?,'PROCESSING',?,?,?)",
              id,
              sessionId,
              actor.tenant(),
              actor.subject(),
              key,
              hash,
              request.mode().name(),
              Jsons.write(state),
              Timestamp.from(now),
              Timestamp.from(deadline));
          jdbc.update(
              "UPDATE sessions SET active_run=?,updated_at=? WHERE id=?",
              id,
              Timestamp.from(now),
              sessionId);
          audit(actor.tenant(), id, "RUN_STARTED", actor.subject());
          return new Started(load(id), true);
        });
  }

  public Run visible(UUID id, Actor actor) {
    Run run = load(id);
    if (!run.tenant().equals(actor.tenant())
        || (!run.requester().equals(actor.subject()) && !actor.approver()))
      throw ApiException.missing();
    return run;
  }

  public Run load(UUID id) {
    var rows = jdbc.query("SELECT * FROM runs WHERE id=?", this::mapRun, id);
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.get(0);
  }

  public RunResponse view(Run run) {
    PendingApprovalResponse pending = null;
    if ("WAITING_APPROVAL".equals(run.status())) {
      var rows =
          jdbc.query(
              "SELECT * FROM approvals WHERE run_id=? AND status='PENDING'",
              (rs, n) ->
                  new PendingApprovalResponse(
                      rs.getString("call_id"),
                      rs.getString("tool"),
                      rs.getString("arguments"),
                      rs.getTimestamp("expires_at").toInstant()),
              run.id());
      if (!rows.isEmpty()) pending = rows.get(0);
    }
    var s = run.state();
    var credits =
        jdbc.query(
            "SELECT * FROM service_credits WHERE tenant=? AND run_id=?",
            (rs, n) ->
                new CreditReceiptResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("order_id"),
                    rs.getLong("amount_paise"),
                    rs.getString("approved_by"),
                    rs.getTimestamp("created_at").toInstant()),
            run.tenant(),
            run.id());
    return new RunResponse(
        run.id(),
        run.sessionId(),
        run.status(),
        s.answer,
        pending,
        new TokenUsageResponse(
            s.inputTokens,
            s.cachedInputTokens,
            s.outputTokens,
            s.reservedTokens,
            s.estimatedCost.toPlainString()),
        credits,
        run.errorCode(),
        run.createdAt());
  }

  public void checkpoint(Run run) {
    int changed =
        jdbc.update(
            "UPDATE runs SET state_json=? WHERE id=? AND status='PROCESSING' AND deadline>?",
            Jsons.write(run.state()),
            run.id(),
            Timestamp.from(Instant.now()));
    if (changed != 1) throw ApiException.conflict("RUN_STOPPED", "Run is no longer executable");
  }

  public void waitForApproval(Run run, ModelPort.ToolCall call) {
    tx.executeWithoutResult(
        txStatus -> {
          lockSession(run.sessionId());
          Run current = lockRun(run.id());
          requireProcessing(current);
          Instant expiry = Instant.now().plus(config.approvalTtl());
          jdbc.update(
              "INSERT INTO approvals(run_id,status,call_id,tool,arguments,expires_at)"
                  + " VALUES(?,'PENDING',?,?,?,?)",
              run.id(),
              call.id(),
              call.name(),
              call.arguments(),
              Timestamp.from(expiry));
          jdbc.update(
              "UPDATE runs SET status='WAITING_APPROVAL',state_json=?,deadline=? WHERE id=?",
              Jsons.write(run.state()),
              Timestamp.from(expiry),
              run.id());
          audit(run.tenant(), run.id(), "APPROVAL_REQUESTED", run.requester());
        });
  }

  /** Approval and the local ledger effect commit atomically. The model cannot call this method. */
  public Run decide(UUID id, Actor actor, DecisionRequest decision) {
    if (!actor.approver())
      throw new ApiException(
          HttpStatus.FORBIDDEN, "APPROVER_REQUIRED", "Approver permission required");
    Run observed = visible(id, actor);
    return tx.execute(
        status -> {
          lockSession(observed.sessionId());
          Run run = lockRun(id);
          if (run.requester().equals(actor.subject()))
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "FOUR_EYES_REQUIRED",
                "A different person must approve this request");
          if (!"WAITING_APPROVAL".equals(run.status()) || !Instant.now().isBefore(run.deadline()))
            throw ApiException.conflict(
                "APPROVAL_NOT_PENDING", "Approval is expired or has already been decided");
          PendingApprovalResponse pending =
              jdbc.queryForObject(
                  "SELECT * FROM approvals WHERE run_id=? AND status='PENDING' FOR UPDATE",
                  (rs, n) ->
                      new PendingApprovalResponse(
                          rs.getString("call_id"),
                          rs.getString("tool"),
                          rs.getString("arguments"),
                          rs.getTimestamp("expires_at").toInstant()),
                  id);
          ToolExecutor.Reply reply;
          if (Boolean.TRUE.equals(decision.approve())) {
            if (!"propose_credit".equals(pending.tool()))
              throw ApiException.conflict("INVALID_APPROVAL", "Unsupported pending action");
            var args = ToolCatalog.validate(pending.tool(), pending.arguments());
            var orders =
                jdbc.query(
                    "SELECT status,total_paise FROM service_orders WHERE tenant=? AND order_id=?"
                        + " FOR UPDATE",
                    (rs, n) ->
                        Map.<String, Object>of("status", rs.getString(1), "total", rs.getLong(2)),
                    actor.tenant(),
                    args.orderId());
            if (orders.isEmpty()
                || !"DELAYED".equals(orders.get(0).get("status"))
                || args.amountPaise() > (long) orders.get(0).get("total"))
              throw ApiException.conflict(
                  "ORDER_CHANGED", "Order no longer satisfies the credit policy");
            if (jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service_credits WHERE tenant=? AND order_id=?",
                    Long.class,
                    actor.tenant(),
                    args.orderId())
                > 0)
              throw ApiException.conflict(
                  "CREDIT_EXISTS", "A credit has already been recorded for this order");
            UUID credit = UUID.randomUUID();
            jdbc.update(
                "INSERT INTO"
                    + " service_credits(id,tenant,order_id,amount_paise,run_id,approved_by,created_at)"
                    + " VALUES(?,?,?,?,?,?,?)",
                credit,
                actor.tenant(),
                args.orderId(),
                args.amountPaise(),
                id,
                actor.subject(),
                Timestamp.from(Instant.now()));
            reply =
                ToolExecutor.Reply.ok(
                    Map.of(
                        "creditId",
                        credit.toString(),
                        "orderId",
                        args.orderId(),
                        "amountPaise",
                        args.amountPaise(),
                        "status",
                        "RECORDED",
                        "evidenceId",
                        "credit:" + credit));
            run.state().creditRecorded = true;
            run.state().evidence.add("order:" + args.orderId());
          } else
            reply =
                ToolExecutor.Reply.error(
                    "HUMAN_DENIED",
                    false,
                    "Human reviewer declined; do not request this action again in this run");
          ToolExecutor.appendResult(run.state(), pending.callId(), reply);
          jdbc.update(
              "UPDATE approvals SET status=?,decided_by=?,decision_reason=? WHERE run_id=?",
              decision.approve() ? "APPROVED" : "DENIED",
              actor.subject(),
              decision.reason(),
              id);
          jdbc.update(
              "UPDATE runs SET status='PROCESSING',state_json=?,deadline=? WHERE id=?",
              Jsons.write(run.state()),
              Timestamp.from(Instant.now().plus(config.runTimeout())),
              id);
          audit(
              actor.tenant(),
              id,
              decision.approve() ? "CREDIT_RECORDED" : "APPROVAL_DENIED",
              actor.subject());
          return load(id);
        });
  }

  public boolean hasDecision(UUID id) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM approvals WHERE run_id=?", Long.class, id) > 0;
  }

  public void complete(Run run) {
    tx.executeWithoutResult(
        txStatus -> {
          lockSession(run.sessionId());
          Run current = lockRun(run.id());
          requireProcessing(current);
          jdbc.update(
              "UPDATE runs SET status='COMPLETED',state_json=? WHERE id=?",
              Jsons.write(run.state()),
              run.id());
          jdbc.update(
              "UPDATE sessions SET transcript=?,active_run=NULL,updated_at=? WHERE id=? AND"
                  + " active_run=?",
              Jsons.write(run.state().transcript),
              Timestamp.from(Instant.now()),
              run.sessionId(),
              run.id());
          audit(run.tenant(), run.id(), "RUN_COMPLETED", run.requester());
        });
  }

  public void terminal(UUID id, String terminal, String code) {
    Run observed = load(id);
    tx.executeWithoutResult(
        txStatus -> {
          lockSession(observed.sessionId());
          Run run = lockRun(id);
          if (!List.of("PROCESSING", "WAITING_APPROVAL").contains(run.status())) return;
          jdbc.update("UPDATE runs SET status=?,error_code=? WHERE id=?", terminal, code, id);
          jdbc.update(
              "UPDATE approvals SET status='CANCELLED' WHERE run_id=? AND status='PENDING'", id);
          jdbc.update(
              "UPDATE sessions SET active_run=NULL,updated_at=? WHERE id=? AND active_run=?",
              Timestamp.from(Instant.now()),
              run.sessionId(),
              id);
          audit(run.tenant(), id, terminal, run.requester());
        });
  }

  public void cancel(UUID id, Actor actor) {
    Run run = visible(id, actor);
    if (!run.requester().equals(actor.subject())) throw ApiException.missing();
    terminal(id, "CANCELLED", "USER_CANCELLED");
  }

  public void recoverExpired() {
    var ids =
        jdbc.query(
            "SELECT id FROM runs WHERE status IN ('PROCESSING','WAITING_APPROVAL') AND deadline<?",
            (rs, n) -> rs.getObject(1, UUID.class),
            Timestamp.from(Instant.now()));
    for (UUID id : ids) {
      Run run = load(id);
      // Recheck under locks: an approval may have resumed the run since the query.
      tx.executeWithoutResult(
          s -> {
            lockSession(run.sessionId());
            Run current = lockRun(id);
            if (current.deadline().isBefore(Instant.now())) terminal(id, "EXPIRED", "RUN_EXPIRED");
          });
    }
  }

  public void purgeConversations() {
    jdbc.update(
        "DELETE FROM sessions WHERE active_run IS NULL AND updated_at<?",
        Timestamp.from(Instant.now().minus(config.retention())));
  }

  public void deleteSession(UUID id, Actor actor) {
    tx.executeWithoutResult(
        s -> {
          Session session = ownedSession(id, actor, true);
          if (session.activeRun() != null)
            throw ApiException.conflict(
                "SESSION_BUSY", "Cancel the active run before deleting the session");
          jdbc.update("DELETE FROM sessions WHERE id=?", id);
        });
  }

  private Session ownedSession(UUID id, Actor actor, boolean lock) {
    var rows =
        jdbc.query(
            "SELECT * FROM sessions WHERE id=? AND tenant=? AND owner_id=?"
                + (lock ? " FOR UPDATE" : ""),
            (rs, n) ->
                new Session(
                    id,
                    rs.getString("tenant"),
                    rs.getString("owner_id"),
                    rs.getString("transcript"),
                    rs.getObject("active_run", UUID.class)),
            id,
            actor.tenant(),
            actor.subject());
    if (rows.isEmpty()) throw ApiException.missing();
    return rows.get(0);
  }

  private void lockSession(UUID id) {
    jdbc.queryForObject("SELECT id FROM sessions WHERE id=? FOR UPDATE", UUID.class, id);
  }

  private Run lockRun(UUID id) {
    return jdbc.queryForObject("SELECT * FROM runs WHERE id=? FOR UPDATE", this::mapRun, id);
  }

  private void requireProcessing(Run run) {
    if (!"PROCESSING".equals(run.status()))
      throw ApiException.conflict("RUN_STOPPED", "Run is no longer executable");
    BudgetPolicy.checkTime(run.deadline());
  }

  private Run mapRun(ResultSet rs, int row) throws SQLException {
    return new Run(
        rs.getObject("id", UUID.class),
        rs.getObject("session_id", UUID.class),
        rs.getString("tenant"),
        rs.getString("requester"),
        rs.getString("mode"),
        rs.getString("status"),
        Jsons.read(rs.getString("state_json"), RunState.class),
        rs.getString("error_code"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("deadline").toInstant());
  }

  private void audit(String tenant, UUID id, String event, String actor) {
    jdbc.update(
        "INSERT INTO audit_events(id,tenant,run_id,event_type,actor,created_at)"
            + " VALUES(?,?,?,?,?,?)",
        UUID.randomUUID(),
        tenant,
        id,
        event,
        actor,
        Timestamp.from(Instant.now()));
  }

  private static String sha256(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
