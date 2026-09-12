package com.dedeepya.agent.service.impl;

import com.dedeepya.agent.dto.response.SessionResponse;
import com.dedeepya.agent.repository.RunStore;
import com.dedeepya.agent.security.Actor;
import com.dedeepya.agent.service.SessionService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SessionServiceImpl implements SessionService {
  private final RunStore store;

  public SessionServiceImpl(RunStore store) {
    this.store = store;
  }

  @Override
  public SessionResponse create(Actor actor) {
    return new SessionResponse(store.createSession(actor));
  }

  @Override
  public void delete(UUID id, Actor actor) {
    store.deleteSession(id, actor);
  }
}
