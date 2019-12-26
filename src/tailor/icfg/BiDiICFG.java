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

package tailor.icfg;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import soot.Value;
import soot.toolkits.graph.DirectedGraph;

public interface BiDiICFG<N, M> extends ICFG<N, M> {

	public List<N> getPredsOf(N u);
	
	public Collection<N> getEndPointsOf(M m);

	public List<N> getPredsOfCallAt(N u);

	public Set<N> allNonCallEndNodes();
		
	//also exposed to some clients who need it
	public DirectedGraph<N> getOrCreateUnitGraph(M body);

	public List<Value> getParameterRefs(M m);
	
	/**
	 * Gets whether the given statement is a return site of at least one call
	 * @param n The statement to check
	 * @return True if the given statement is a return site, otherwise false
	 */
	public boolean isReturnSite(N n);
	
}
