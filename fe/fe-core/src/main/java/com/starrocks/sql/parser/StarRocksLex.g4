// This file is licensed under the Elastic License 2.0. Copyright 2021-present, StarRocks Limited.

lexer grammar StarRocksLex;
@parser::members {public static long sqlMode;}
tokens {
    CONCAT
}

ALL: 'ALL';
ALTER: 'ALTER';
AND: 'AND';
ANTI: 'ANTI';
ARRAY: 'ARRAY';
AS: 'AS';
ASC: 'ASC';
BETWEEN: 'BETWEEN';
BIGINT: 'BIGINT';
BITMAP: 'BITMAP';
BOOLEAN: 'BOOLEAN';
BUCKETS: 'BUCKETS';
BY: 'BY';
CASE: 'CASE';
CAST: 'CAST';
CHAR: 'CHAR';
COLLATE: 'COLLATE';
CONNECTION_ID: 'CONNECTION_ID';
COMMENT: 'COMMENT';
COSTS: 'COSTS';
CREATE: 'CREATE';
CROSS: 'CROSS';
CUBE: 'CUBE';
CURRENT: 'CURRENT';
CURRENT_USER: 'CURRENT_USER';
DATA: 'DATA';
DATABASE: 'DATABASE';
DATABASES: 'DATABASES';
DATE: 'DATE';
DATETIME: 'DATETIME';
DAY: 'DAY';
DECIMAL: 'DECIMAL';
DECIMALV2: 'DECIMALV2';
DECIMAL32: 'DECIMAL32';
DECIMAL64: 'DECIMAL64';
DECIMAL128: 'DECIMAL128';
DEFAULT: 'DEFAULT';
DENSE_RANK: 'DENSE_RANK';
DESC: 'DESC';
DESCRIBE: 'DESCRIBE';
DISTRIBUTED: 'DISTRIBUTED';
DISTINCT: 'DISTINCT';
DOUBLE: 'DOUBLE';
DUAL: 'DUAL';
ELSE: 'ELSE';
END: 'END';
EXCEPT: 'EXCEPT';
EXISTS: 'EXISTS';
EXPLAIN: 'EXPLAIN';
EXTRACT: 'EXTRACT';
EVERY: 'EVERY';
FALSE: 'FALSE';
FILTER: 'FILTER';
FIRST: 'FIRST';
FIRST_VALUE: 'FIRST_VALUE';
FLOAT: 'FLOAT';
FOLLOWING: 'FOLLOWING';
FOR: 'FOR';
FORMAT: 'FORMAT';
FROM: 'FROM';
FULL: 'FULL';
GLOBAL: 'GLOBAL';
GROUP: 'GROUP';
GROUPING: 'GROUPING';
GROUPING_ID: 'GROUPING_ID';
HASH: 'HASH';
HAVING: 'HAVING';
HLL: 'HLL';
HOUR: 'HOUR';
IF: 'IF';
IN: 'IN';
INNER: 'INNER';
INSERT: 'INSERT';
INT: 'INT';
INTEGER: 'INTEGER';
INTERSECT: 'INTERSECT';
INTERVAL: 'INTERVAL';
INTO: 'INTO';
IS: 'IS';
JOIN: 'JOIN';
JSON: 'JSON';
LABEL: 'LABEL';
LAG: 'LAG';
LARGEINT: 'LARGEINT';
LAST: 'LAST';
LAST_VALUE: 'LAST_VALUE';
LATERAL: 'LATERAL';
LEAD: 'LEAD';
LEFT: 'LEFT';
LESS: 'LESS';
LIKE: 'LIKE';
LIMIT: 'LIMIT';
LOCAL: 'LOCAL';
LOGICAL: 'LOGICAL';
MAXVALUE: 'MAXVALUE';
MINUTE: 'MINUTE';
MINUS: 'MINUS';
MONTH: 'MONTH';
NONE: 'NONE';
NOT: 'NOT';
NULL: 'NULL';
NULLS: 'NULLS';
OFFSET: 'OFFSET';
ON: 'ON';
OR: 'OR';
ORDER: 'ORDER';
OUTER: 'OUTER';
OUTFILE: 'OUTFILE';
OVER: 'OVER';
PARTITION: 'PARTITION';
PARTITIONS: 'PARTITIONS';
PASSWORD: 'PASSWORD';
PRECEDING: 'PRECEDING';
PERCENTILE: 'PERCENTILE';
PROPERTIES: 'PROPERTIES';
RANGE: 'RANGE';
RANK: 'RANK';
REGEXP: 'REGEXP';
RIGHT: 'RIGHT';
RLIKE: 'RLIKE';
ROLLUP: 'ROLLUP';
ROW: 'ROW';
ROWS: 'ROWS';
ROW_NUMBER: 'ROW_NUMBER';
SCHEMA: 'SCHEMA';
SECOND: 'SECOND';
SELECT: 'SELECT';
SEMI: 'SEMI';
SESSION: 'SESSION';
SET: 'SET';
SETS: 'SETS';
SET_VAR: 'SET_VAR';
SHOW: 'SHOW';
SMALLINT: 'SMALLINT';
START: 'START';
STRING: 'STRING';
TABLE: 'TABLE';
TABLES: 'TABLES';
TABLET: 'TABLET';
TEMPORARY: 'TEMPORARY';
THAN: 'THAN';
THEN: 'THEN';
TIME: 'TIME';
TIMESTAMPADD: 'TIMESTAMPADD';
TIMESTAMPDIFF: 'TIMESTAMPDIFF';
TINYINT: 'TINYINT';
TRUE: 'TRUE';
TYPE: 'TYPE';
UNBOUNDED: 'UNBOUNDED';
UNION: 'UNION';
USE: 'USE';
USER: 'USER';
USING: 'USING';
VARCHAR: 'VARCHAR';
VALUES: 'VALUES';
VERBOSE: 'VERBOSE';
VIEW: 'VIEW';
WEEK: 'WEEK';
WHEN: 'WHEN';
WHERE: 'WHERE';
WITH: 'WITH';
YEAR: 'YEAR';

EQ  : '=';
NEQ : '<>' | '!=';
LT  : '<';
LTE : '<=';
GT  : '>';
GTE : '>=';
EQ_FOR_NULL: '<=>';

PLUS_SYMBOL: '+';
MINUS_SYMBOL: '-';
ASTERISK_SYMBOL: '*';
SLASH_SYMBOL: '/';
PERCENT_SYMBOL: '%';
LOGICAL_OR: '||' {setType((StarRocksParser.sqlMode & com.starrocks.qe.SqlModeHelper.MODE_PIPES_AS_CONCAT) == 0 ? LOGICAL_OR : StarRocksParser.CONCAT);};

INT_DIV: 'DIV';
BITAND: '&';
BITOR: '|';
BITXOR: '^';
BITNOT: '~';
LOGICAL_NOT: '!';
ARROW: '->';
AT: '@';

SINGLE_QUOTED_TEXT
    : '\'' ( ~'\'' | '\'\'' )* '\''
    ;

DOUBLE_QUOTED_TEXT
    : '"' ( '\\'. | '""' | ~('"'| '\\') )* '"'
    ;

INTEGER_VALUE
    : DIGIT+
    ;

DECIMAL_VALUE
    : DIGIT+ '.' DIGIT*
    | '.' DIGIT+
    ;

DOUBLE_VALUE
    : DIGIT+ ('.' DIGIT*)? EXPONENT
    | '.' DIGIT+ EXPONENT
    ;

IDENTIFIER
    : (LETTER | '_') (LETTER | DIGIT | '_' | '@' | ':')*
    ;

DIGIT_IDENTIFIER
    : DIGIT (LETTER | DIGIT | '_' | '@' | ':')+
    ;

QUOTED_IDENTIFIER
    : '"' ( ~'"' | '""' )* '"'
    ;

BACKQUOTED_IDENTIFIER
    : '`' ( ~'`' | '``' )* '`'
    ;

fragment EXPONENT
    : 'E' [+-]? DIGIT+
    ;

fragment DIGIT
    : [0-9]
    ;

fragment LETTER
    : [a-zA-Z_$\u0080-\uffff]
    ;

SIMPLE_COMMENT
    : '--' ~[\r\n]* '\r'? '\n'? -> channel(HIDDEN)
    ;

BRACKETED_COMMENT
    : '/*' ~'+' .*? '*/' -> channel(HIDDEN)
    ;

SEMICOLON: ';';

WS
    : [ \r\n\t]+ -> channel(HIDDEN)
    ;