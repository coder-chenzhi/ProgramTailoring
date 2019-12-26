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

import static java.util.Collections.singleton;
import heros.FlowFunction;
import heros.FlowFunctions;

import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import tailor.icfg.ICFG;
import tailor.ifds.IFDSTopDownTailor;
import tailor.ifds.StatementSequence;
import tailor.ifds.StatementSequenceUtil;

public class IFDSExtensionTopDownTailor extends IFDSTopDownTailor {
	
	public IFDSExtensionTopDownTailor(ICFG<Unit, SootMethod> icfg,
			StatementSequenceUtil util,
			Set<StatementSequence> initialFacts) {
		super(icfg, util, initialFacts);
	}
	
	protected class ExtensionTopDownFlowFunctions extends TopDownFlowFunctions {
		
		@Override
		public FlowFunction<StatementSequence> getCallFlowFunction(
				Unit callStmt, SootMethod destinationMethod) {			
			return new FlowFunction<StatementSequence>() {
				@Override
				public Set<StatementSequence> computeTargets(StatementSequence source) {
					if (util.canRemoveHead(callStmt, source)) {
						return singleton(source.removeHead());
					} else {
						return singleton(source);
					}
				}
			};
		}
	}
	
	@Override
	protected FlowFunctions<Unit, StatementSequence, SootMethod> createFlowFunctionsFactory() {
		return new ExtensionTopDownFlowFunctions();
	}
}
