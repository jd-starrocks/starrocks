// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Limited.
package com.starrocks.sql.analyzer;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.starrocks.analysis.BinaryPredicate;
import com.starrocks.analysis.CompoundPredicate;
import com.starrocks.analysis.DescribeStmt;
import com.starrocks.analysis.Expr;
import com.starrocks.analysis.LikePredicate;
import com.starrocks.analysis.LiteralExpr;
import com.starrocks.analysis.OrderByElement;
import com.starrocks.analysis.Predicate;
import com.starrocks.analysis.SetType;
import com.starrocks.analysis.ShowAlterStmt;
import com.starrocks.analysis.ShowAuthenticationStmt;
import com.starrocks.analysis.ShowColumnStmt;
import com.starrocks.analysis.ShowCreateDbStmt;
import com.starrocks.analysis.ShowCreateTableStmt;
import com.starrocks.analysis.ShowDataStmt;
import com.starrocks.analysis.ShowDbStmt;
import com.starrocks.analysis.ShowDeleteStmt;
import com.starrocks.analysis.ShowDynamicPartitionStmt;
import com.starrocks.analysis.ShowFunctionsStmt;
import com.starrocks.analysis.ShowIndexStmt;
import com.starrocks.analysis.ShowLoadStmt;
import com.starrocks.analysis.ShowLoadWarningsStmt;
import com.starrocks.analysis.ShowMaterializedViewStmt;
import com.starrocks.analysis.ShowPartitionsStmt;
import com.starrocks.analysis.ShowProcStmt;
import com.starrocks.analysis.ShowRoutineLoadStmt;
import com.starrocks.analysis.ShowStmt;
import com.starrocks.analysis.ShowTableStatusStmt;
import com.starrocks.analysis.ShowTableStmt;
import com.starrocks.analysis.ShowTabletStmt;
import com.starrocks.analysis.ShowVariablesStmt;
import com.starrocks.analysis.SlotRef;
import com.starrocks.analysis.StringLiteral;
import com.starrocks.analysis.TableName;
import com.starrocks.analysis.UserIdentity;
import com.starrocks.catalog.Column;
import com.starrocks.catalog.Database;
import com.starrocks.catalog.KeysType;
import com.starrocks.catalog.MaterializedIndex;
import com.starrocks.catalog.MaterializedIndexMeta;
import com.starrocks.catalog.MysqlTable;
import com.starrocks.catalog.OlapTable;
import com.starrocks.catalog.Table;
import com.starrocks.catalog.Type;
import com.starrocks.common.AnalysisException;
import com.starrocks.common.ErrorCode;
import com.starrocks.common.ErrorReport;
import com.starrocks.common.proc.ExternalTableProcDir;
import com.starrocks.common.proc.PartitionsProcDir;
import com.starrocks.common.proc.ProcNodeInterface;
import com.starrocks.common.proc.ProcService;
import com.starrocks.common.proc.TableProcDir;
import com.starrocks.common.util.OrderByPair;
import com.starrocks.qe.ConnectContext;
import com.starrocks.server.CatalogMgr;
import com.starrocks.server.GlobalStateMgr;
import com.starrocks.sql.ast.AstVisitor;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.starrocks.common.ErrorCode.ERR_UNSUPPORTED_SQL_PATTERN;

public class ShowStmtAnalyzer {

    public static void analyze(ShowStmt stmt, ConnectContext session) {
        new ShowStmtAnalyzerVisitor().analyze(stmt, session);
    }

    static class ShowStmtAnalyzerVisitor extends AstVisitor<Void, ConnectContext> {

        private static final Logger logger = LoggerFactory.getLogger(ShowStmtAnalyzerVisitor.class);

        public void analyze(ShowStmt statement, ConnectContext session) {
            analyzeShowPredicate(statement);
            visit(statement, session);
        }

        @Override
        public Void visitShowTableStmt(ShowTableStmt node, ConnectContext context) {
            String db = node.getDb();
            db = getDatabaseName(db, context);
            node.setDb(db);
            return null;
        }

        @Override
        public Void visitShowTabletStmt(ShowTabletStmt node, ConnectContext context) {
            ShowTabletStmtAnalyzer.analyze(node, context);
            return null;
        }

        @Override
        public Void visitShowVariablesStmt(ShowVariablesStmt node, ConnectContext context) {
            if (node.getType() == null) {
                node.setType(SetType.DEFAULT);
            }
            return null;
        }

        @Override
        public Void visitShowColumnStmt(ShowColumnStmt node, ConnectContext context) {
            node.init();
            String db = node.getTableName().getDb();
            db = getDatabaseName(db, context);
            node.getTableName().setDb(db);
            return null;
        }

        @Override
        public Void visitShowTableStatusStmt(ShowTableStatusStmt node, ConnectContext context) {
            String db = node.getDb();
            db = getDatabaseName(db, context);
            node.setDb(db);
            return null;
        }

        @Override
        public Void visitShowFunctions(ShowFunctionsStmt node, ConnectContext context) {
            String dbName = node.getDbName();
            if (Strings.isNullOrEmpty(dbName)) {
                dbName = context.getDatabase();
                if (Strings.isNullOrEmpty(dbName)) {
                    ErrorReport.reportSemanticException(ErrorCode.ERR_NO_DB_ERROR);
                }
            }
            node.setDbName(dbName);

            if (node.getExpr() != null) {
                ErrorReport.reportSemanticException(ERR_UNSUPPORTED_SQL_PATTERN);
            }
            return null;
        }

        @Override
        public Void visitShowMaterializedViewStmt(ShowMaterializedViewStmt node, ConnectContext context) {
            String db = node.getDb();
            db = getDatabaseName(db, context);
            node.setDb(db);
            return null;
        }

        @Override
        public Void visitShowCreateTableStmt(ShowCreateTableStmt node, ConnectContext context) {
            if (node.getTbl() == null) {
                ErrorReport.reportSemanticException(ErrorCode.ERR_NO_TABLES_USED);
            }
            node.getTbl().normalization(context);
            return null;
        }

        @Override
        public Void visitShowDatabasesStmt(ShowDbStmt node, ConnectContext context) {
            String catalogName;
            if (node.getCatalogName() != null) {
                catalogName = node.getCatalogName();
            } else {
                catalogName = context.getCurrentCatalog();
            }
            if (!GlobalStateMgr.getCurrentState().getCatalogMgr().catalogExists(catalogName)) {
                ErrorReport.reportSemanticException(ErrorCode.ERR_BAD_CATALOG_ERROR, catalogName);
            }
            return null;
        }

        @Override
        public Void visitShowRoutineLoadStatement(ShowRoutineLoadStmt node, ConnectContext context) {
            String dbName = node.getDbFullName();
            dbName = getDatabaseName(dbName, context);
            node.setDb(dbName);
            return null;
        }

        @Override
        public Void visitShowAlterStmt(ShowAlterStmt statement, ConnectContext context) {
            ShowAlterStmtAnalyzer.analyze(statement, context);
            return null;
        }

        @Override
        public Void visitShowDeleteStmt(ShowDeleteStmt node, ConnectContext context) {
            String dbName = node.getDbName();
            dbName = getDatabaseName(dbName, context);
            node.setDbName(dbName);
            return null;
        }

        @Override
        public Void visitShowDynamicPartitionStatement(ShowDynamicPartitionStmt node, ConnectContext context) {
            String dbName = node.getDb();
            dbName = getDatabaseName(dbName, context);
            node.setDb(dbName);
            return null;
        }

        @Override
        public Void visitShowIndexStmt(ShowIndexStmt node, ConnectContext context) {
            node.init();
            String db = node.getTableName().getDb();
            db = getDatabaseName(db, context);
            node.getTableName().setDb(db);
            if (Strings.isNullOrEmpty(node.getTableName().getCatalog())) {
                node.getTableName().setCatalog(context.getCurrentCatalog());
            }
            return null;
        }

        // used for remove default_cluster from stmt
        String getDatabaseName(String db, ConnectContext session) {
            if (Strings.isNullOrEmpty(db)) {
                db = session.getDatabase();
                if (Strings.isNullOrEmpty(db)) {
                    ErrorReport.reportSemanticException(ErrorCode.ERR_NO_DB_ERROR);
                }
            }
            return db;
        }

        @Override
        public Void visitShowCreateDbStatement(ShowCreateDbStmt node, ConnectContext context) {
            String dbName = node.getDb();
            dbName = getDatabaseName(dbName, context);
            node.setDb(dbName);
            return null;
        }

        @Override
        public Void visitShowDataStmt(ShowDataStmt node, ConnectContext context) {
            String dbName = node.getDbName();
            dbName = getDatabaseName(dbName, context);
            node.setDbName(dbName);
            return null;
        }

        @Override
        public Void visitDescTableStmt(DescribeStmt node, ConnectContext context) {
            node.getDbTableName().normalization(context);
            TableName tableName = node.getDbTableName();
            String catalogName = tableName.getCatalog();
            String dbName = tableName.getDb();
            String tbl = tableName.getTbl();
            if (catalogName == null) {
                catalogName = context.getCurrentCatalog();
            }

            CatalogMgr catalogMgr = GlobalStateMgr.getCurrentState().getCatalogMgr();

            if (!catalogMgr.catalogExists(catalogName)) {
                ErrorReport.reportSemanticException(ErrorCode.ERR_BAD_CATALOG_ERROR, catalogName);
            }

            if (CatalogMgr.isInternalCatalog(catalogName)) {
                descInternalTbl(node, context);
            } else {
                descExternalTbl(node, catalogName, dbName, tbl);
            }
            return null;
        }

        private void descInternalTbl(DescribeStmt node, ConnectContext context) {
            Database db = GlobalStateMgr.getCurrentState().getDb(node.getDb());
            if (db == null) {
                ErrorReport.reportSemanticException(ErrorCode.ERR_BAD_DB_ERROR, node.getDb());
            }
            db.readLock();
            try {
                Table table = db.getTable(node.getTableName());
                //if getTable not find table, may be is statement "desc materialized-view-name"
                if (table == null) {
                    for (Table tb : db.getTables()) {
                        if (tb.getType() == Table.TableType.OLAP) {
                            OlapTable olapTable = (OlapTable) tb;
                            for (MaterializedIndex mvIdx : olapTable.getVisibleIndex()) {
                                if (olapTable.getIndexNameById(mvIdx.getId()).equalsIgnoreCase(node.getTableName())) {
                                    List<Column> columns = olapTable.getIndexIdToSchema().get(mvIdx.getId());
                                    for (Column column : columns) {
                                        // Extra string (aggregation and bloom filter)
                                        List<String> extras = Lists.newArrayList();
                                        if (column.getAggregationType() != null &&
                                                olapTable.getKeysType() != KeysType.PRIMARY_KEYS) {
                                            extras.add(column.getAggregationType().name());
                                        }
                                        String defaultStr = column.getMetaDefaultValue(extras);
                                        String extraStr = StringUtils.join(extras, ",");
                                        List<String> row = Arrays.asList(
                                                column.getDisplayName(),
                                                column.getType().canonicalName(),
                                                column.isAllowNull() ? "Yes" : "No",
                                                ((Boolean) column.isKey()).toString(),
                                                defaultStr,
                                                extraStr);
                                        node.getTotalRows().add(row);
                                    }
                                    node.setMaterializedView(true);
                                    return;
                                }
                            }
                        }
                    }
                    ErrorReport.reportSemanticException(ErrorCode.ERR_BAD_TABLE_ERROR, node.getTableName());
                }

                if (table.getType() == Table.TableType.HIVE || table.getType() == Table.TableType.HUDI
                        || table.getType() == Table.TableType.ICEBERG) {
                    // Reuse the logic of `desc <table_name>` because hive/hudi/iceberg external table doesn't support view.
                    node.setAllTables(false);
                }

                if (!node.isAllTables()) {
                    // show base table schema only
                    String procString = "/dbs/" + db.getId() + "/" + table.getId() + "/" + TableProcDir.INDEX_SCHEMA
                            + "/";
                    if (table.getType() == Table.TableType.OLAP) {
                        procString += ((OlapTable) table).getBaseIndexId();
                    } else {
                        procString += table.getId();
                    }

                    try {
                        node.setNode(ProcService.getInstance().open(procString));
                    } catch (AnalysisException e) {
                        throw new SemanticException(String.format("Unknown proc node path: %s", procString));
                    }
                } else {
                    if (table.isNativeTable()) {
                        node.setOlapTable(true);
                        OlapTable olapTable = (OlapTable) table;
                        Set<String> bfColumns = olapTable.getCopiedBfColumns();
                        Map<Long, List<Column>> indexIdToSchema = olapTable.getIndexIdToSchema();

                        // indices order
                        List<Long> indices = Lists.newArrayList();
                        indices.add(olapTable.getBaseIndexId());
                        for (Long indexId : indexIdToSchema.keySet()) {
                            if (indexId != olapTable.getBaseIndexId()) {
                                indices.add(indexId);
                            }
                        }

                        // add all indices
                        for (int i = 0; i < indices.size(); ++i) {
                            long indexId = indices.get(i);
                            List<Column> columns = indexIdToSchema.get(indexId);
                            String indexName = olapTable.getIndexNameById(indexId);
                            MaterializedIndexMeta indexMeta = olapTable.getIndexMetaByIndexId(indexId);
                            for (int j = 0; j < columns.size(); ++j) {
                                Column column = columns.get(j);

                                // Extra string (aggregation and bloom filter)
                                List<String> extras = Lists.newArrayList();
                                if (column.getAggregationType() != null &&
                                        olapTable.getKeysType() != KeysType.PRIMARY_KEYS) {
                                    extras.add(column.getAggregationType().name());
                                }
                                if (bfColumns != null && bfColumns.contains(column.getName())) {
                                    extras.add("BLOOM_FILTER");
                                }
                                String defaultStr = column.getMetaDefaultValue(extras);
                                String extraStr = StringUtils.join(extras, ",");
                                List<String> row = Arrays.asList("",
                                        "",
                                        column.getDisplayName(),
                                        column.getType().canonicalName(),
                                        column.isAllowNull() ? "Yes" : "No",
                                        ((Boolean) column.isKey()).toString(),
                                        defaultStr,
                                        extraStr);

                                if (j == 0) {
                                    row.set(0, indexName);
                                    row.set(1, indexMeta.getKeysType().name());
                                }

                                node.getTotalRows().add(row);
                            } // end for columns

                            if (i != indices.size() - 1) {
                                node.getTotalRows().add(node.EMPTY_ROW);
                            }
                        } // end for indices
                    } else if (table.getType() == Table.TableType.MYSQL) {
                        node.setOlapTable(false);
                        MysqlTable mysqlTable = (MysqlTable) table;
                        List<String> row = Arrays.asList(mysqlTable.getHost(),
                                mysqlTable.getPort(),
                                mysqlTable.getUserName(),
                                mysqlTable.getPasswd(),
                                mysqlTable.getMysqlDatabaseName(),
                                mysqlTable.getMysqlTableName());
                        node.getTotalRows().add(row);
                    } else {
                        ErrorReport.reportSemanticException(ErrorCode.ERR_UNKNOWN_STORAGE_ENGINE, table.getType());
                    }
                }
            } finally {
                db.readUnlock();
            }
        }

        private void descExternalTbl(DescribeStmt node, String catalogName, String dbName, String tbl) {
            // show external table schema only
            String procString = "/catalog/" + catalogName + "/" + dbName + "/" + tbl + "/" + ExternalTableProcDir.SCHEMA;
            try {
                node.setNode(ProcService.getInstance().open(procString));
            } catch (AnalysisException e) {
                throw new SemanticException(String.format("Unknown proc node path: %s", procString));
            }
        }

        @Override
        public Void visitShowProcStmt(ShowProcStmt node, ConnectContext context) {
            String path = node.getPath();
            if (Strings.isNullOrEmpty(path)) {
                throw new SemanticException("Path is null");
            }
            try {
                node.setNode(ProcService.getInstance().open(path));
            } catch (AnalysisException e) {
                throw new SemanticException(String.format("Unknown proc node path: %s", path));
            }
            return null;
        }

        private void analyzeShowPredicate(ShowStmt showStmt) {
            Predicate predicate = showStmt.getPredicate();
            if (predicate == null) {
                return;
            }

            if (!(predicate instanceof BinaryPredicate) || !((BinaryPredicate) predicate).getOp().isEquivalence()) {
                throw new SemanticException("Only support equal predicate in show statement");
            }

            BinaryPredicate binaryPredicate = (BinaryPredicate) predicate;
            if (!(binaryPredicate.getChild(0) instanceof SlotRef && binaryPredicate.getChild(1) instanceof LiteralExpr)) {
                throw new SemanticException("Only support column = \"string literal\" format predicate");
            }
        }

        @Override
        public Void visitShowPartitionsStmt(ShowPartitionsStmt statement, ConnectContext context) {
            String dbName = statement.getDbName();
            dbName = getDatabaseName(dbName, context);
            statement.setDbName(dbName);
            final Map<String, Expr> filterMap = statement.getFilterMap();
            if (statement.getWhereClause() != null) {
                analyzeSubPredicate(filterMap, statement.getWhereClause());
            }
            Database db = GlobalStateMgr.getCurrentState().getDb(dbName);
            if (db == null) {
                ErrorReport.reportSemanticException(ErrorCode.ERR_BAD_DB_ERROR, dbName);
            }
            final String tableName = statement.getTableName();
            final boolean isTempPartition = statement.isTempPartition();
            db.readLock();
            try {
                Table table = db.getTable(tableName);
                if (!(table instanceof OlapTable)) {
                    throw new SemanticException("Table[" + tableName + "] does not exists or is not OLAP table");
                }

                // build proc path
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("/dbs/");
                stringBuilder.append(db.getId());
                stringBuilder.append("/").append(table.getId());
                if (isTempPartition) {
                    stringBuilder.append("/temp_partitions");
                } else {
                    stringBuilder.append("/partitions");
                }

                logger.debug("process SHOW PROC '{}';", stringBuilder);

                try {
                    statement.setNode(ProcService.getInstance().open(stringBuilder.toString()));
                } catch (AnalysisException e) {
                    throw new SemanticException("get the PROC Node by the path %s error: %s", stringBuilder,
                            e.getMessage());
                }

                final List<OrderByPair> orderByPairs =
                        analyzeOrderBy(statement.getOrderByElements(), statement.getNode());
                statement.setOrderByPairs(orderByPairs);
            } finally {
                db.readUnlock();
            }
            return null;
        }

        private void analyzeSubPredicate(Map<String, Expr> filterMap, Expr subExpr) {
            if (subExpr == null) {
                return;
            }
            if (subExpr instanceof CompoundPredicate) {
                CompoundPredicate cp = (CompoundPredicate) subExpr;
                if (cp.getOp() != CompoundPredicate.Operator.AND) {
                    throw new SemanticException("Only allow compound predicate with operator AND");
                }
                analyzeSubPredicate(filterMap, cp.getChild(0));
                analyzeSubPredicate(filterMap, cp.getChild(1));
                return;
            }

            if (!(subExpr.getChild(0) instanceof SlotRef)) {
                throw new SemanticException("Show filter by column");
            }

            String leftKey = ((SlotRef) subExpr.getChild(0)).getColumnName();
            boolean filter = leftKey.equalsIgnoreCase(ShowPartitionsStmt.FILTER_PARTITION_NAME) ||
                    leftKey.equalsIgnoreCase(ShowPartitionsStmt.FILTER_STATE);
            if (subExpr instanceof BinaryPredicate) {
                binaryPredicateHandler(subExpr, leftKey, filter);
            } else if (subExpr instanceof LikePredicate) {
                likePredicateHandler((LikePredicate) subExpr, filter);
            } else {
                throw new SemanticException("Only operator =|>=|<=|>|<|!=|like are supported.");
            }
            filterMap.put(leftKey.toLowerCase(), subExpr);
        }

        private void likePredicateHandler(LikePredicate subExpr, boolean filter) {
            if (filter && subExpr.getOp() != LikePredicate.Operator.LIKE) {
                throw new SemanticException("Where clause : PartitionName|State like \"p20191012|NORMAL\"");
            }
            if (!filter) {
                throw new SemanticException("Where clause : PartitionName|State like \"p20191012|NORMAL\"");
            }
        }

        private void binaryPredicateHandler(Expr subExpr, String leftKey, boolean filter) {
            BinaryPredicate binaryPredicate = (BinaryPredicate) subExpr;
            if (filter && binaryPredicate.getOp() != BinaryPredicate.Operator.EQ) {
                throw new SemanticException(String.format("Only operator =|like are supported for %s", leftKey));
            }
            if (leftKey.equalsIgnoreCase(ShowPartitionsStmt.FILTER_LAST_CONSISTENCY_CHECK_TIME)) {
                if (!(subExpr.getChild(1) instanceof StringLiteral)) {
                    throw new SemanticException("Where clause : LastConsistencyCheckTime =|>=|<=|>|<|!= "
                            + "\"2019-12-22|2019-12-22 22:22:00\"");
                }
                try {
                    subExpr.setChild(1, (subExpr.getChild(1)).castTo(Type.DATETIME));
                } catch (AnalysisException e) {
                    throw new SemanticException("expression %s cast to datetime error: %s",
                            subExpr.getChild(1).toString(), e.getMessage());
                }
            } else if (ShowPartitionsStmt.FILTER_COLUMNS.stream()
                    .noneMatch(column -> column.equalsIgnoreCase(leftKey))) {
                throw new SemanticException("Only the columns of PartitionId/PartitionName/" +
                        "State/Buckets/ReplicationNum/LastConsistencyCheckTime are supported.");
            }
        }

        /**
         * analyze order by clause if not null and init the orderByPairs
         */
        private List<OrderByPair> analyzeOrderBy(List<OrderByElement> orderByElements, ProcNodeInterface node) {
            List<OrderByPair> orderByPairs = new ArrayList<>();
            if (orderByElements != null && !orderByElements.isEmpty()) {
                for (OrderByElement orderByElement : orderByElements) {
                    if (!(orderByElement.getExpr() instanceof SlotRef)) {
                        throw new SemanticException("Should order by column");
                    }
                    SlotRef slotRef = (SlotRef) orderByElement.getExpr();
                    int index = ((PartitionsProcDir) node).analyzeColumn(slotRef.getColumnName());
                    OrderByPair orderByPair = new OrderByPair(index, !orderByElement.getIsAsc());
                    orderByPairs.add(orderByPair);
                }
            }
            return orderByPairs;
        }

        public Void visitShowLoadStmt(ShowLoadStmt statement, ConnectContext context) {
            ShowLoadStmtAnalyzer.analyze(statement, context);
            return null;
        }

        @Override
        public Void visitShowLoadWarningsStmt(ShowLoadWarningsStmt statement, ConnectContext context) {
            ShowLoadWarningsStmtAnalyzer.analyze(statement, context);
            return null;
        }

        @Override
        public Void visitShowAuthenticationStatement(ShowAuthenticationStmt statement, ConnectContext context) {
            UserIdentity user = statement.getUserIdent();
            if (user != null) {
                try {
                    user.analyze();
                } catch (AnalysisException e) {
                    SemanticException exception = new SemanticException("failed to show authentication for " + user.toString());
                    exception.initCause(e);
                    throw exception;
                }
            } else if (! statement.isAll()) {
                statement.setUserIdent(context.getCurrentUserIdentity());
            }
            return null;
        }

    }
}
