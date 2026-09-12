package com.dedeepya.agent.domain.port;

import java.util.Map;

public interface PolicyGateway {
  Map<String, Object> lookup(String topic);

  static Map<String, Object> policy() {
    return Map.of(
        "evidenceId",
        "policy:service_credit:v1",
        "topic",
        "service_credit",
        "maxAmountPaise",
        50000,
        "currency",
        "INR",
        "eligibleStatus",
        "DELAYED",
        "humanApprovalRequired",
        true,
        "policyText",
        "One service credit per delayed order, at most INR 500 and no more than the order value. A"
            + " different authorized person must approve.");
  }
}
