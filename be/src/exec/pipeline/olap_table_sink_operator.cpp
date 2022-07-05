// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Limited.

#include "exec/pipeline/olap_table_sink_operator.h"

#include "column/chunk.h"
#include "exec/tablet_sink.h"
#include "exprs/expr.h"
#include "runtime/buffer_control_block.h"
#include "runtime/exec_env.h"
#include "runtime/query_statistics.h"
#include "runtime/result_buffer_mgr.h"
#include "runtime/runtime_state.h"

namespace starrocks::pipeline {
Status OlapTableSinkOperator::prepare(RuntimeState* state) {
    Operator::prepare(state);

    RETURN_IF_ERROR(_sink->try_open(state));

    return Status::OK();
}

void OlapTableSinkOperator::close(RuntimeState* state) {
    Operator::close(state);
}

StatusOr<vectorized::ChunkPtr> OlapTableSinkOperator::pull_chunk(RuntimeState* state) {
    return Status::NotSupported("Shouldn't pull chunk from olap table sink operator");
}

bool OlapTableSinkOperator::is_finished() const {
    return _is_finished;
}

bool OlapTableSinkOperator::pending_finish() const {
    if (!_sink->is_close_done()) {
        auto st = _sink->try_close(_fragment_ctx->runtime_state());
        if (!st.ok()) {
            _fragment_ctx->cancel(st);
            return false;
        }
        return true;
    }

    auto st = _sink->close(_fragment_ctx->runtime_state(), Status::OK());
    if (!st.ok()) {
        _fragment_ctx->cancel(st);
    }

    return false;
}

Status OlapTableSinkOperator::set_cancelled(RuntimeState* state) {
    return _sink->close(state, Status::Cancelled("Cancelled by pipeline engine"));
}

Status OlapTableSinkOperator::set_finishing(RuntimeState* state) {
    if (!_is_open_done) {
        // _is_open_done indicates the reponse of open() is returned or not
        RETURN_IF_ERROR(_sink->open_wait());
        _is_open_done = true;
    }

    _is_finished = true;

    return _sink->try_close(state);
}

bool OlapTableSinkOperator::need_input() const {
    if (is_finished()) {
        return false;
    }
    if (!_is_open_done) {
        return _sink->is_open_done();
    }
    return !_sink->is_full();
}

Status OlapTableSinkOperator::push_chunk(RuntimeState* state, const vectorized::ChunkPtr& chunk) {
    if (!_is_open_done) {
        // _is_open_done indicates the reponse of open() is returned or not
        RETURN_IF_ERROR(_sink->open_wait());
        _is_open_done = true;
    }

    uint16_t num_rows = chunk->num_rows();
    if (num_rows == 0) {
        return Status::OK();
    }

    // send_chunk() use internal queue, we check is_full() before call send_chunk(), so it will not block
    return _sink->send_chunk(state, chunk.get());
}

OperatorPtr OlapTableSinkOperatorFactory::create(int32_t degree_of_parallelism, int32_t driver_sequence) {
    DCHECK_EQ(degree_of_parallelism, 1);
    return std::make_shared<OlapTableSinkOperator>(this, _id, _plan_node_id, driver_sequence, _sink, _fragment_ctx);
}

Status OlapTableSinkOperatorFactory::prepare(RuntimeState* state) {
    RETURN_IF_ERROR(OperatorFactory::prepare(state));

    RETURN_IF_ERROR(_sink->prepare(state));

    return Status::OK();
}

void OlapTableSinkOperatorFactory::close(RuntimeState* state) {
    OperatorFactory::close(state);
}
} // namespace starrocks::pipeline
