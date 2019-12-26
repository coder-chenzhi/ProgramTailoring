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

package tailor.ifds;

import static java.util.Collections.singleton;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.flowfunc.Identity;
import heros.flowfunc.KillAll;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.DefaultJimpleIFDSTabulationProblem;
import tailor.icfg.ICFG;

public class IFDSTopDownTailor
	extends
	DefaultJimpleIFDSTabulationProblem<StatementSequence, ICFG<Unit, SootMethod>> {

	protected StatementSequenceUtil util;
	protected Set<StatementSequence> initialFacts; // initial facts at entry point
	
	public IFDSTopDownTailor(ICFG<Unit, SootMethod> icfg,
			StatementSequenceUtil util,
			Set<StatementSequence> initialFacts) {
		super(icfg);
		this.util = util;
		this.initialFacts = initialFacts;
	}

	@Override
	public Map<Unit, Set<StatementSequence>> initialSeeds() {
		initialFacts.add(zeroValue());
		Map<Unit, Set<StatementSequence>> res =
				new HashMap<Unit, Set<StatementSequence>>();
		res.put(Scene.v()
				.getMainMethod()
				.getActiveBody()
				.getUnits()
				.getFirst(), initialFacts);
		return res;
	}

	protected class TopDownFlowFunctions implements FlowFunctions<Unit, StatementSequence, SootMethod> {
		@Override
		public FlowFunction<StatementSequence> getNormalFlowFunction(
				Unit curr, Unit succ) {
			return Identity.v();
		}

		@Override
		public FlowFunction<StatementSequence> getCallFlowFunction(
				Unit callStmt, SootMethod destinationMethod) {
			return Identity.v();
		}

		@Override
		public FlowFunction<StatementSequence> getReturnFlowFunction(
				Unit callSite, SootMethod calleeMethod, Unit exitStmt,
				Unit returnSite) {
			return Identity.v();
		}

		@Override
		public FlowFunction<StatementSequence> getCallToReturnFlowFunction(
				Unit callSite, Unit returnSite) {
			if (util.isUnitOfSC(callSite)) {
				return new FlowFunction<StatementSequence>() {
					@Override
					public Set<StatementSequence> computeTargets(StatementSequence source) {
						if (util.canRemoveHead(callSite, source)) {
							return singleton(source.removeHead());
						} else {
							return singleton(source);
						}
					}
				};
			} else {
				// no callees handling
				boolean shouldKill = false;
				Collection<SootMethod> callees = interproceduralCFG().getCalleesOfCallAt(callSite);
				if (!callees.isEmpty()) {
					for (SootMethod callee : callees) {
						// We found that <clinit> bounded with native method calls in Soot call graph
						// the recursion caused by <clinit>-><clinit> is not real at runtime in this case
						// as the <clinit> is explicitly introduced by Soot at native call sites.
						if (callee.hasActiveBody() && !callee.getName().equals("<clinit>")) {
							shouldKill = true;
							break;
						}
					}
				}
				if (shouldKill) {
					return KillAll.v();
				} else {
					return Identity.v();
				}
			}
		}
	}
	
	@Override
	protected FlowFunctions<Unit, StatementSequence, SootMethod> createFlowFunctionsFactory() {
		return new TopDownFlowFunctions();
	}

	@Override
	protected StatementSequence createZeroValue() {
		return util.createZeroValue();
	}
}
