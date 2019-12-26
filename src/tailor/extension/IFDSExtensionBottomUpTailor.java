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

package tailor.extension;

import static heros.TwoElementSet.twoElementSet;
import static java.util.Collections.singleton;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.flowfunc.KillAll;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import soot.SootMethod;
import soot.Unit;
import tailor.icfg.BackwardsICFG;
import tailor.ifds.IFDSBottomUpTailor;
import tailor.ifds.StatementSequence;
import tailor.ifds.StatementSequenceUtil;

public class IFDSExtensionBottomUpTailor extends IFDSBottomUpTailor {

	private Set<StatementSequence> facts = Collections.newSetFromMap(new ConcurrentHashMap<>());
	
	public IFDSExtensionBottomUpTailor(BackwardsICFG icfg,
			StatementSequenceUtil util, Set<Unit> initialUnits) {
		super(icfg, util, initialUnits);
	}

	protected class ExtensionBottomUpFlowFunctions extends BottomUpFlowFunctions {
		
		@Override
		public FlowFunction<StatementSequence> getReturnFlowFunction(
				Unit callSite, SootMethod calleeMethod, Unit exitStmt,
				Unit returnSite) {
			addReachableTag(exitStmt);
			// NOTICE: `callSite` is the true return site, and 
			// `returnSite` is the true call site.
			if (fwICFG.getCallersOf(calleeMethod).contains(returnSite)) {
				return new FlowFunction<StatementSequence>() {
					@Override
					public Set<StatementSequence> computeTargets(StatementSequence source) {
						if (util.canExtend(returnSite, source)) {
//							showExtensionUpdate(apiCall, source);
							return twoElementSet(source, source.addHead(returnSite));
						}
						return singleton(source);
					}
				};
			} else {
				return KillAll.v();
			}
		}
	}
	
	@Override
	protected FlowFunctions<Unit, StatementSequence, SootMethod> createFlowFunctionsFactory() {
		return new ExtensionBottomUpFlowFunctions();
	}
	
	protected void showExtensionUpdate(Unit apiCall, StatementSequence source) {
		StatementSequence ret = source.addHead(apiCall);
		if (!facts.contains(ret)) {
			facts.add(ret);
			synchronized (this) {
				System.out.println("[Ext-rela]: " + ret.length());
				ret.getStmtSeq().forEach(unit -> {
					System.out.print("  > ");
					System.out.println(
							unitToString(unit, fwICFG.getMethodOf(unit)));
					System.out.flush();
				});
			}
		}
	}
	
	public static String unitToString(Unit unit, SootMethod inMethod) {
		return inMethod.getDeclaringClass().getName()
				+ "." + inMethod.getName()
				+ " (Ln:" + unit.getJavaSourceStartLineNumber()
				+ ") -> " + unit;
	}
}
