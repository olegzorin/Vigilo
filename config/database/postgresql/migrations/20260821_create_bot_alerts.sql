CREATE TABLE bot_alerts
(
    alert_id            varchar(36) not null,
    idempotency_key     varchar(250) not null,
    payload_hash        varchar(64) not null,
    organization_id     int not null,
    location_id         int not null,
    device_uuid         varchar(250),
    alert_type          varchar(50) not null,
    severity            varchar(20) not null,
    status              varchar(20) not null,
    rule_id             varchar(100) not null,
    occurred_at         timestamp(3) not null,
    created_at          timestamp(3) not null,
    bot_assignment_id   int not null,
    bot_id              int not null,
    bot_version_id      int not null,
    run_id              bigint not null,
    event_key           varchar(100) not null,
    changes_json        text not null,
    acknowledged_at     timestamp(3),
    acknowledged_by     int,
    resolved_at         timestamp(3),
    resolved_by         int,
    PRIMARY KEY (alert_id)
);

CREATE UNIQUE INDEX ui_bot_alerts_idempotency ON bot_alerts (idempotency_key);
CREATE INDEX i_bot_alerts_location_status ON bot_alerts (location_id, status, occurred_at);
CREATE INDEX i_bot_alerts_device ON bot_alerts (device_uuid, occurred_at);
