/* Tailor - Program Tailoring: Slicing by Sequential Criteria
 *
 * Copyright (C) 2016 Yue Li, Tian Tan, Jingling Xue
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package tailor.extension.tagger;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import tailor.tag.ConstructorCallTag;

public enum ConstructorCallTagger implements MethodTagger {
	// Enable singleton pattern
	INSTANCE;
	
	@Override
	public void tag(SootMethod m) {
		if (!m.hasTag(ConstructorCallTag.NAME)) {
			DirectedGraph<Unit> graph = new BriefUnitGraph(m.getActiveBody());
			graph.forEach(unit -> {
				if (isNewStmt(unit)) {
					unit.addTag(ConstructorCallTag.getInstance(
							findConstructorCall((AssignStmt) unit, graph)));
				}
			});
			// This tag marks whether a method has been tagged.
			m.addTag(ConstructorCallTag.getInstance(null));
		}
	}

	public static boolean isNewStmt(Unit u) {
		return getNewExpr(u) != null;
	}
	
	public static NewExpr getNewExpr(Unit u) {
		if (u instanceof AssignStmt) {
			AssignStmt assign = (AssignStmt) u;
			Value rightOp = assign.getRightOp();
			if (rightOp instanceof NewExpr) {
				return (NewExpr) rightOp;
			}
		}
		return null;
	}
	
	public Unit findConstructorCall(AssignStmt newStmt, DirectedGraph<Unit> graph) {
		Value left = newStmt.getLeftOp();
		Set<Unit> visited = new HashSet<>();
		Stack<Unit> stack = new Stack<>();
		stack.push(newStmt);
		while (!stack.empty()) {
			Unit unit = stack.pop();
			visited.add(unit);
			if (unit instanceof Stmt) {
				Stmt stmt = (Stmt) unit;
				if (stmt.containsInvokeExpr()
						&& stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
					InstanceInvokeExpr invokeExpr =
							(InstanceInvokeExpr) stmt.getInvokeExpr();
					if (invokeExpr.getBase().equals(left)
							&& invokeExpr.getMethod().isConstructor()) {
						return unit;
					}
				}
			}
			graph.getSuccsOf(unit).forEach(succ -> {
				if (!visited.contains(succ)) {
					stack.push(succ);
				}
			});
		}
		throw new RuntimeException("Could not find the constructor call of " + newStmt);
	}
}
