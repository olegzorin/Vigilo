BEGIN;

ALTER TABLE bot_latest_runs
    RENAME COLUMN message_number_offset TO next_input_number;

ALTER TABLE bot_input_messages_p
    RENAME COLUMN message_number TO input_number;
ALTER TABLE bot_input_messages_p
    RENAME COLUMN is_large_message TO is_large_input;
ALTER TABLE bot_input_messages_p
    RENAME COLUMN message_size TO input_size;
ALTER TABLE bot_input_messages_p
    RENAME COLUMN message_body TO input_data;
ALTER TABLE bot_input_messages_p
    RENAME COLUMN large_message_body TO large_input_data;

ALTER TABLE bot_input_messages_p RENAME TO bot_pending_inputs_p;
ALTER TABLE bot_input_messages_p_p0 RENAME TO bot_pending_inputs_p_p0;
ALTER TABLE bot_input_messages_p_p1 RENAME TO bot_pending_inputs_p_p1;
ALTER TABLE bot_input_messages_p_p2 RENAME TO bot_pending_inputs_p_p2;
ALTER TABLE bot_input_messages_p_p3 RENAME TO bot_pending_inputs_p_p3;
ALTER INDEX i_bot_input_messages_p_bot_assignment RENAME TO i_bot_pending_inputs_p_bot_assignment;
ALTER INDEX bot_input_messages_p_p0_bot_assignment_id_idx RENAME TO bot_pending_inputs_p_p0_bot_assignment_id_idx;
ALTER INDEX bot_input_messages_p_p1_bot_assignment_id_idx RENAME TO bot_pending_inputs_p_p1_bot_assignment_id_idx;
ALTER INDEX bot_input_messages_p_p2_bot_assignment_id_idx RENAME TO bot_pending_inputs_p_p2_bot_assignment_id_idx;
ALTER INDEX bot_input_messages_p_p3_bot_assignment_id_idx RENAME TO bot_pending_inputs_p_p3_bot_assignment_id_idx;

COMMIT;
