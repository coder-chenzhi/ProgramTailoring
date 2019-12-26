/* Tailor - Program Tailoring: by Sequential Criteria
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

import heros.DontSynchronize;
import heros.SynchronizedBy;
import heros.ThreadSafe;
import heros.solver.IDESolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import soot.Body;
import soot.Kind;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;
import soot.jimple.toolkits.callgraph.Filter;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;


/**
 * Default implementation for the {@link ICFG} interface.
 * Includes all statements reachable from {@link Scene#getEntryPoints()} through
 * explicit call statements or through calls to {@link Thread#start()}.
 * 
 * This class is designed to be thread safe, and subclasses of this class must be designed
 * in a thread-safe way, too.
 */
@ThreadSafe
public class JimpleBasedICFG extends AbstractJimpleBasedICFG {
	
	//retains only callers that are explicit call sites or Thread.start()
	public static class EdgeFilter extends Filter {		
		protected EdgeFilter() {
			super(new EdgePredicate() {
				@Override
				public boolean want(Edge e) {
					// add reflective calls
					return e.kind() == Kind.REFL_CLASS_NEWINSTANCE
							|| e.kind() == Kind.REFL_CONSTR_NEWINSTANCE
							|| e.kind() == Kind.REFL_INVOKE
							|| e.kind().isExplicit() || e.kind().isThread() || e.kind().isExecutor()
							|| e.kind().isAsyncTask() || e.kind().isClinit() || e.kind().isPrivileged();
				}
			});
		}
	}
	
	@DontSynchronize("readonly")
	protected final CallGraph cg;
	
	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<Unit,Collection<SootMethod>> unitToCallees =
			IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<Unit,Collection<SootMethod>>() {
				@Override
				public Collection<SootMethod> load(Unit u) throws Exception {
					ArrayList<SootMethod> res = null;
					//only retain callers that are explicit call sites or Thread.start()
					Iterator<Edge> edgeIter = new EdgeFilter().wrap(cg.edgesOutOf(u));					
					while(edgeIter.hasNext()) {
						Edge edge = edgeIter.next();
						SootMethod m = edge.getTgt().method();
						if(m.hasActiveBody()) {
							if (res == null)
								res = new ArrayList<SootMethod>();
							res.add(m);
						}
						else if(IDESolver.DEBUG) 
							System.err.println("Method "+m.getSignature()+" is referenced but has no body!");
					}
					
					if (res != null) {
						res.trimToSize();
						return res;
					}
					else
						return Collections.emptySet();
				}
			});

	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<SootMethod,Collection<Unit>> methodToCallers =
			IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<SootMethod,Collection<Unit>>() {
				@Override
				public Collection<Unit> load(SootMethod m) throws Exception {
					ArrayList<Unit> res = new ArrayList<Unit>();
					//only retain callers that are explicit call sites or Thread.start()
					Iterator<Edge> edgeIter = new EdgeFilter().wrap(cg.edgesInto(m));					
					while(edgeIter.hasNext()) {
						Edge edge = edgeIter.next();
						res.add(edge.srcUnit());
					}
					res.trimToSize();
					return res;
				}
			});

	public JimpleBasedICFG() {
		cg = Scene.v().getCallGraph();		
		initializeUnitToOwner();
	}

	protected void initializeUnitToOwner() {
		for(Iterator<MethodOrMethodContext> iter = Scene.v().getReachableMethods().listener(); iter.hasNext(); ) {
			SootMethod m = iter.next().method();
			initializeUnitToOwner(m);
		}
	}
	
	public void initializeUnitToOwner(SootMethod m) {
		if(m.hasActiveBody()) {
			Body b = m.getActiveBody();
			PatchingChain<Unit> units = b.getUnits();
			for (Unit unit : units) {
				unitToOwner.put(unit, b);
			}
		}
	}

	@Override
	public Collection<SootMethod> getCalleesOfCallAt(Unit u) {
		return unitToCallees.getUnchecked(u);
	}

	@Override
	public Collection<Unit> getCallersOf(SootMethod m) {
		return methodToCallers.getUnchecked(m);
	}
	
}
