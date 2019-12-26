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

import static heros.TwoElementSet.twoElementSet;
import static java.util.Collections.singleton;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.flowfunc.Identity;
import heros.flowfunc.KillAll;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.DefaultJimpleIFDSTabulationProblem;
import tailor.icfg.BackwardsICFG;
import tailor.icfg.ICFG;
import tailor.tag.MainReachableTag;

public class IFDSBottomUpTailor
		extends
		DefaultJimpleIFDSTabulationProblem<StatementSequence, ICFG<Unit, SootMethod>> {

	protected ICFG<Unit, SootMethod> fwICFG;
	protected StatementSequenceUtil util;
	protected Set<Unit> initialUnits; // entry points of Bottom-Up analysis
	
	public IFDSBottomUpTailor(BackwardsICFG icfg,
			StatementSequenceUtil util,
			Set<Unit> initialUnits) {
		super(icfg);
		this.fwICFG = icfg.getForwardInterproceduralCFG();
		this.util = util;
		this.initialUnits = initialUnits;
	}

	@Override
	public Map<Unit, Set<StatementSequence>> initialSeeds() {
		Map<Unit, Set<StatementSequence>> res = new HashMap<Unit, Set<StatementSequence>>();
		for (Unit u : initialUnits) {
			res.put(u, singleton(zeroValue()));
		}
		return res;
	}

	protected class BottomUpFlowFunctions implements FlowFunctions<Unit, StatementSequence, SootMethod> {
		@Override
		public FlowFunction<StatementSequence> getNormalFlowFunction(
				Unit curr, Unit succ) {
			addReachableTag(curr);
			return Identity.v();
		}

		@Override
		public FlowFunction<StatementSequence> getCallFlowFunction(
				Unit callStmt, SootMethod destinationMethod) {
			// the calls to API method have been ignored by BlockedICFG
			addReachableTag(callStmt);
			return Identity.v();
		}

		@Override
		public FlowFunction<StatementSequence> getReturnFlowFunction(
				Unit callSite, SootMethod calleeMethod, Unit exitStmt,
				Unit returnSite) {
			addReachableTag(exitStmt);
			// NOTICE: `callSite` is the true return site, and 
			// `returnSite` is the true call site.
			if (fwICFG.getCallersOf(calleeMethod).contains(returnSite)) {
				// When the ICFG is inverted, the facts should flow to
				// only feasible `return site`.
				return Identity.v();
			} else {
				return KillAll.v();
			}
		}

		@Override
		public FlowFunction<StatementSequence> getCallToReturnFlowFunction(
				Unit callSite, Unit returnSite) {
			addReachableTag(callSite);
			// NOTICE: `callSite` is the true return site, and 
			// `returnSite` is the true call site.
			if (util.isUnitOfSC(returnSite)) {
				return new FlowFunction<StatementSequence>() {
					@Override
					public Set<StatementSequence> computeTargets(StatementSequence source) {
						if (source == zeroValue()) {
							if (util.isTail(returnSite)) {
								return singleton(source.addHead(returnSite));
							}
						} else if (util.canAddHead(returnSite, source)) {
							return twoElementSet(source, source.addHead(returnSite));
						}
						return singleton(source);
					}
				};
			} else {
				 // Identity can avoid the situation where
				 // the "return-site" has no callees (i.e., lack of method body).
				 // But the facts have to be propagated.
				 // The similar situation in Top-down algorithm is different.
				return Identity.v();
			}
		}
	}
	
	@Override
	protected FlowFunctions<Unit, StatementSequence, SootMethod> createFlowFunctionsFactory() {
		return new BottomUpFlowFunctions();
	}

	@Override
	protected StatementSequence createZeroValue() {
		return util.createZeroValue();
	}
	
	protected void addReachableTag(Unit u) {
		if (!u.hasTag(MainReachableTag.NAME)) {
			u.addTag(MainReachableTag.INSTANCE);
		}
	}
}
