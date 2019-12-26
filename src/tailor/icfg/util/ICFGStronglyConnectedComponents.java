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

package tailor.icfg.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import tailor.icfg.ICFG;

public class ICFGStronglyConnectedComponents<N, M> {

	protected final List<Set<M>> componentList = new ArrayList<>();
	protected final List<Set<M>> trueComponentList = new ArrayList<>();
	
	protected int index = 0;
	protected Map<M, Integer> indexForNode, lowlinkForNode;
	protected Stack<M> s;
	
	protected ICFG<N, M> g;
	
	/**
      *  @param g a graph for which we want to compute the strongly
      *           connected components. 
      *  @see ICFG
      */
	public ICFGStronglyConnectedComponents(ICFG<N, M> g, M head) {
		this.g = g;
		s = new Stack<M>();
		
		indexForNode = new HashMap<>();
		lowlinkForNode = new HashMap<>();
		
		recurse(head);
		
		//free memory
	    indexForNode = null;
	    lowlinkForNode = null;
	    s = null;
	    g = null;
	}
	
	public List<Set<M>> getComponents() {
		return componentList;
	}

	public List<Set<M>> getTrueComponents() {
		return trueComponentList;
	}
	
	public Set<M> trueComponentContains(M m) {
		for (Set<M> component : trueComponentList) {
			if (component.contains(m)) {
				return component;
			}
		}
		return null;
	}
	
	protected void recurse(M v) {
		indexForNode.put(v, index);
	    lowlinkForNode.put(v, index);
	    ++index;
	    s.push(v);	    
	    getSuccsOf(v).forEach(callee -> {
	    	if (!indexForNode.containsKey(callee)) {
    			recurse(callee);
    			lowlinkForNode.put(v, Math.min(lowlinkForNode.get(v), lowlinkForNode.get(callee)));
    		} else if (s.contains(callee)) {
    			lowlinkForNode.put(v, Math.min(lowlinkForNode.get(v), indexForNode.get(callee)));
    		}
	    });
	    if(lowlinkForNode.get(v).intValue() == indexForNode.get(v).intValue()) {
	    	Set<M> scc = new HashSet<>();
	    	M v2;
	    	do {
	    		v2 = s.pop();
	    		scc.add(v2);
	    	} while (v != v2);
	    	componentList.add(scc);
	    	if (scc.size() > 1) {
	    		trueComponentList.add(scc);
	    	} else {
	    		scc.forEach(n -> {
		    		if (getSuccsOf(n).contains(n)) {
		    			trueComponentList.add(scc);
		    		}
	    		});
	    	}
	    }
	}
	
	protected Collection<M> getSuccsOf(M v) {
		return g.getCallsFromWithin(v)
				.stream()
				.map(call -> g.getCalleesOfCallAt(call))
				.reduce(new HashSet<>(), (s1, s2) -> { s1.addAll(s2); return s1; });	
	}
}
