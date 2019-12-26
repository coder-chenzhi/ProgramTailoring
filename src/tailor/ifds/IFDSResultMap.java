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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.JimpleIFDSSolver;

import tailor.icfg.ICFG;

public class IFDSResultMap {

	Map<Set<StatementSequence>, Set<StatementSequence>> resultSetPool = new HashMap<>();
	Map<Unit, Set<StatementSequence>> resultMap = new HashMap<>(); 
	
	public IFDSResultMap(JimpleIFDSSolver<StatementSequence,ICFG<Unit,SootMethod>> solver,
			ICFG<Unit,SootMethod> icfg) {
		icfg.allMethods().forEach(m -> {
			m.getActiveBody().getUnits().forEach(u -> {
				resultMap.put(u, getFactSet(solver.ifdsResultsAt(u)));
			});
		});
	}
	
	public Set<StatementSequence> ifdsResultsAt(Unit u) {
		return resultMap.get(u);
	}
	
	private Set<StatementSequence> getFactSet(Set<StatementSequence> factSet) {
		if (!resultSetPool.containsKey(factSet)) {
			resultSetPool.put(factSet, factSet);
		}
		return resultSetPool.get(factSet);
	}

}
