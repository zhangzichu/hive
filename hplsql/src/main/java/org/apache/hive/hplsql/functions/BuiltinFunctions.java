/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one
 *  * or more contributor license agreements.  See the NOTICE file
 *  * distributed with this work for additional information
 *  * regarding copyright ownership.  The ASF licenses this file
 *  * to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance
 *  * with the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.apache.hive.hplsql.functions;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ParserRuleContext;
import org.apache.hive.hplsql.Exec;
import org.apache.hive.hplsql.HplsqlParser;
import org.apache.hive.hplsql.Query;
import org.apache.hive.hplsql.Utils;
import org.apache.hive.hplsql.Var;

public class BuiltinFunctions {
  protected final Exec exec;
  protected boolean trace;
  protected HashMap<String, FuncCommand> map = new HashMap<>();
  protected HashMap<String, FuncSpecCommand> specMap = new HashMap<>();
  protected HashMap<String, FuncSpecCommand> specSqlMap = new HashMap<>();

  public BuiltinFunctions(Exec exec) {
    this.exec = exec;
    trace = exec.getTrace();
  }

  public void register(BuiltinFunctions f) {
  }

  public boolean exec(String name, HplsqlParser.Expr_func_paramsContext ctx) {
    if (name.contains(".")) {               // Name can be qualified and spaces are allowed between parts
      String[] parts = name.split("\\.");
      StringBuilder str = new StringBuilder();
      for (int i = 0; i < parts.length; i++) {
        if (i > 0) {
          str.append(".");
        }
        str.append(parts[i].trim());
      }
      name = str.toString();
    }
    if (trace && ctx != null && ctx.parent != null && ctx.parent.parent instanceof HplsqlParser.Expr_stmtContext) {
      trace(ctx, "FUNC " + name);
    }
    FuncCommand func = map.get(name.toUpperCase());
    if (func != null) {
      func.run(ctx);
      return true;
    }
    else {
      return false;
    }
  }

  public boolean exists(String name) {
    if (name == null) {
      return false;
    }
    name = name.toUpperCase();
    return map.containsKey(name) || specMap.containsKey(name) || specSqlMap.containsKey(name);
  }

  /**
   * Execute a special function
   */
  public void specExec(HplsqlParser.Expr_spec_funcContext ctx) {
    String name = ctx.start.getText().toUpperCase();
    if (trace && ctx.parent.parent instanceof HplsqlParser.Expr_stmtContext) {
      trace(ctx, "FUNC " + name);
    }
    FuncSpecCommand func = specMap.get(name);
    if (func != null) {
      func.run(ctx);
    } else if (ctx.T_MAX_PART_STRING() != null) {
      execMaxPartString(ctx);
    } else if (ctx.T_MIN_PART_STRING() != null) {
      execMinPartString(ctx);
    } else if (ctx.T_MAX_PART_INT() != null) {
      execMaxPartInt(ctx);
    } else if (ctx.T_MIN_PART_INT() != null) {
      execMinPartInt(ctx);
    } else if (ctx.T_MAX_PART_DATE() != null) {
      execMaxPartDate(ctx);
    } else if (ctx.T_MIN_PART_DATE() != null) {
      execMinPartDate(ctx);
    } else if (ctx.T_PART_LOC() != null) {
      execPartLoc(ctx);
    } else {
      evalNull();
    }
  }

  /**
   * Execute a special function in executable SQL statement
   */
  public void specExecSql(HplsqlParser.Expr_spec_funcContext ctx) {
    String name = ctx.start.getText().toUpperCase();
    if (trace && ctx.parent.parent instanceof HplsqlParser.Expr_stmtContext) {
      trace(ctx, "FUNC " + name);
    }
    FuncSpecCommand func = specSqlMap.get(name);
    if (func != null) {
      func.run(ctx);
    }
    else {
      exec.stackPush(Exec.getFormattedText(ctx));
    }
  }

  /**
   * Get the current date
   */
  public void execCurrentDate(HplsqlParser.Expr_spec_funcContext ctx) {
    if (trace) {
      trace(ctx, "CURRENT_DATE");
    }
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
    String s = f.format(Calendar.getInstance().getTime());
    exec.stackPush(new Var(Var.Type.DATE, Utils.toDate(s)));
  }

  /**
   * Execute MAX_PART_STRING function
   */
  public void execMaxPartString(HplsqlParser.Expr_spec_funcContext ctx) {
    if (trace) {
      trace(ctx, "MAX_PART_STRING");
    }
    execMinMaxPart(ctx, Var.Type.STRING, true /*max*/);
  }

  /**
   * Execute MIN_PART_STRING function
   */
  public void execMinPartString(HplsqlParser.Expr_spec_funcContext ctx) {
    if (trace) {
      trace(ctx, "MIN_PART_STRING");
    }
    execMinMaxPart(ctx, Var.Type.STRING, false /*max*/);
  }

  /**
   * Execute MAX_PART_INT function
   */
  public void execMaxPartInt(HplsqlParser.Expr_spec_funcContext ctx) {
    if (trace) {
      trace(ctx, "MAX_PART_INT");
    }
    execMinMaxPart(ctx, Var.Type.BIGINT, true /*max*/);
  }

  /**
   * Execute MIN_PART_INT function
   */
  public void execMinPartInt(HplsqlParser.Expr_spec_funcContext ctx) {
    if (trace) {
      trace(ctx, "MIN_PART_INT");
    }
    execMinMaxPart(ctx, Var.Type.BIGINT, false /*max*/);
  }

  /**
   * Execute MAX_PART_DATE function
   */
  public void execMaxPartDate(HplsqlParser.Expr_spec_funcContext ctx) {
    if (trace) {
      trace(ctx, "MAX_PART_DATE");
    }
    execMinMaxPart(ctx, Var.Type.DATE, true /*max*/);
  }

  /**
   * Execute MIN_PART_DATE function
   */
  public void execMinPartDate(HplsqlParser.Expr_spec_funcContext ctx) {
    if (trace) {
      trace(ctx, "MIN_PART_DATE");
    }
    execMinMaxPart(ctx, Var.Type.DATE, false /*max*/);
  }

  /**
   * Execute MIN or MAX partition function
   */
  public void execMinMaxPart(HplsqlParser.Expr_spec_funcContext ctx, Var.Type type, boolean max) {
    String tabname = evalPop(ctx.expr(0)).toString();
    StringBuilder sql = new StringBuilder("SHOW PARTITIONS " + tabname);
    String colname = null;
    int colnum = -1;
    int exprnum = ctx.expr().size();
    // Column name
    if (ctx.expr(1) != null) {
      colname = evalPop(ctx.expr(1)).toString();
    } else {
      colnum = 0;
    }
    // Partition filter
    if (exprnum >= 4) {
      sql.append(" PARTITION (");
      int i = 2;
      while (i + 1 < exprnum) {
        String fcol = evalPop(ctx.expr(i)).toString();
        String fval = evalPop(ctx.expr(i + 1)).toSqlString();
        if (i > 2) {
          sql.append(", ");
        }
        sql.append(fcol).append("=").append(fval);
        i += 2;
      }
      sql.append(")");
    }
    if (trace) {
      trace(ctx, "Query: " + sql);
    }
    if (exec.getOffline()) {
      evalNull();
      return;
    }
    Query query = exec.executeQuery(ctx, sql.toString(), exec.conf.defaultConnection);
    if (query.error()) {
      evalNullClose(query, exec.conf.defaultConnection);
      return;
    }
    ResultSet rs = query.getResultSet();
    try {
      String resultString = null;
      Long resultInt = null;
      Date resultDate = null;
      while (rs.next()) {
        String[] parts = rs.getString(1).split("/");
        // Find partition column by name
        if (colnum == -1) {
          for (int i = 0; i < parts.length; i++) {
            String[] name = parts[i].split("=");
            if (name[0].equalsIgnoreCase(colname)) {
              colnum = i;
              break;
            }
          }
          // No partition column with the specified name exists
          if (colnum == -1) {
            evalNullClose(query, exec.conf.defaultConnection);
            return;
          }
        }
        String[] pair = parts[colnum].split("=");
        if (type == Var.Type.STRING) {
          resultString = Utils.minMaxString(resultString, pair[1], max);
        } else if (type == Var.Type.BIGINT) {
          resultInt = Utils.minMaxInt(resultInt, pair[1], max);
        } else if (type == Var.Type.DATE) {
          resultDate = Utils.minMaxDate(resultDate, pair[1], max);
        }
      }
      if (resultString != null) {
        evalString(resultString);
      } else if (resultInt != null) {
        evalInt(resultInt);
      } else if (resultDate != null) {
        evalDate(resultDate);
      } else {
        evalNull();
      }
    } catch (SQLException e) {
    }
    exec.closeQuery(query, exec.conf.defaultConnection);
  }

  /**
   * Execute PART_LOC function
   */
  public void execPartLoc(HplsqlParser.Expr_spec_funcContext ctx) {
    String tabname = evalPop(ctx.expr(0)).toString();
    StringBuilder sql = new StringBuilder("DESCRIBE EXTENDED " + tabname);
    int exprnum = ctx.expr().size();
    boolean hostname = false;
    // Partition filter
    if (exprnum > 1) {
      sql.append(" PARTITION (");
      int i = 1;
      while (i + 1 < exprnum) {
        String col = evalPop(ctx.expr(i)).toString();
        String val = evalPop(ctx.expr(i + 1)).toSqlString();
        if (i > 2) {
          sql.append(", ");
        }
        sql.append(col).append("=").append(val);
        i += 2;
      }
      sql.append(")");
    }
    // With host name
    if (exprnum % 2 == 0 && evalPop(ctx.expr(exprnum - 1)).intValue() == 1) {
      hostname = true;
    }
    if (trace) {
      trace(ctx, "Query: " + sql);
    }
    if (exec.getOffline()) {
      evalNull();
      return;
    }
    Query query = exec.executeQuery(ctx, sql.toString(), exec.conf.defaultConnection);
    if (query.error()) {
      evalNullClose(query, exec.conf.defaultConnection);
      return;
    }
    String result = null;
    ResultSet rs = query.getResultSet();
    try {
      while (rs.next()) {
        if (rs.getString(1).startsWith("Detailed Partition Information")) {
          Matcher m = Pattern.compile(".*, location:(.*?),.*").matcher(rs.getString(2));
          if (m.find()) {
            result = m.group(1);
          }
        }
      }
    } catch (SQLException e) {
    }
    if (result != null) {
      // Remove the host name
      if (!hostname) {
        Matcher m = Pattern.compile(".*://.*?(/.*)").matcher(result);
        if (m.find()) {
          result = m.group(1);
        }
      }
      evalString(result);
    } else {
      evalNull();
    }
    exec.closeQuery(query, exec.conf.defaultConnection);
  }

  public void trace(ParserRuleContext ctx, String message) {
    if (trace) {
      exec.trace(ctx, message);
    }
  }

  protected void evalNull() {
    exec.stackPush(Var.Null);
  }

  protected void evalString(String string) {
    exec.stackPush(new Var(string));
  }

  protected Var evalPop(ParserRuleContext ctx) {
    exec.visit(ctx);
    return exec.stackPop();
  }

  protected void evalInt(Long i) {
    exec.stackPush(new Var(i));
  }

  protected void evalDate(Date date) {
    exec.stackPush(new Var(Var.Type.DATE, date));
  }

  protected void evalNullClose(Query query, String conn) {
    exec.stackPush(Var.Null);
    exec.closeQuery(query, conn);
    if(trace) {
      query.printStackTrace();
    }
  }

  protected void evalVar(Var var) {
    exec.stackPush(var);
  }

  protected void evalString(StringBuilder string) {
    evalString(string.toString());
  }

  protected void evalInt(int i) {
    evalInt(Long.valueOf(i));
  }

  protected Var evalPop(ParserRuleContext ctx, int value) {
    if (ctx != null) {
      return evalPop(ctx);
    }
    return new Var(Long.valueOf(value));
  }

  /**
   * Get the number of parameters in function call
   */
  public static int getParamCount(HplsqlParser.Expr_func_paramsContext ctx) {
    if (ctx == null) {
      return 0;
    }
    return ctx.func_param().size();
  }

  protected void eval(ParserRuleContext ctx) {
    exec.visit(ctx);
  }

  protected Integer visit(ParserRuleContext ctx) {
    return exec.visit(ctx);
  }

  protected void info(ParserRuleContext ctx, String message) {
    exec.info(ctx, message);
  }
}
