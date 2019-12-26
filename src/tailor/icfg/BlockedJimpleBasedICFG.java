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

import heros.solver.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.Stmt;
import tailor.Options;


/**
 * The ICFG which ignores the callee if it is declared as 
 * one of the ("API") inputs of tailor analysis. 
 * Such ICFG (with "API" callees blocked) is introduced to
 * avoid modifying the IDE solver.
 * 
 */

public abstract class BlockedJimpleBasedICFG extends JimpleBasedICFG {

	protected Set<Unit> specifiedUnits;
	
	protected Set<SootMethod> reflectionSpecAPIs = new HashSet<>();
	protected Set<SootMethod> alwaysBlockedMethods = new HashSet<>();
	
	// for breaking intra-procedural cycles
	protected Map<Unit, List<Unit>> replacedSuccs = new HashMap<>();
	protected Map<Unit, List<Unit>> replacedPreds = new HashMap<>();
	// for breaking inter-procedural cycles
	protected Map<Unit, Set<SootMethod>> blockedCallees = new HashMap<>();
	protected Map<SootMethod, Set<Unit>> blockedCallers = new HashMap<>();
	
	protected Map<Unit, Set<SootMethod>> clinitCallees = new HashMap<>();
	protected Map<SootMethod, Set<Unit>> clinitCallers = new HashMap<>();
	
	protected Map<Unit, Set<SootMethod>> threadCallees = new HashMap<>();
	protected Map<SootMethod, Set<Unit>> threadCallers = new HashMap<>();
	
	protected boolean excludeLibrary = false;

	public static final SootMethod CONSTRUCTOR_NEWINSTANCE = Scene.v().getMethod("<java.lang.reflect.Constructor: java.lang.Object newInstance(java.lang.Object[])>");
	public static final SootMethod CLASS_NEWINSTANCE = Scene.v().getMethod("<java.lang.Class: java.lang.Object newInstance()>");
	public static final SootMethod INVOKE = Scene.v().getMethod("<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>");
	public static final SootMethod GETMETHOD = Scene.v().getMethod("<java.lang.Class: java.lang.reflect.Method getMethod(java.lang.String,java.lang.Class[])>");
	public static final SootMethod GETCLASS = Scene.v().getMethod("<java.lang.Object: java.lang.Class getClass()>");
	public static final SootMethod FORNAME2 = Scene.v().getMethod("<java.lang.Class: java.lang.Class forName(java.lang.String,boolean,java.lang.ClassLoader)>");
	public static final SootMethod FORNAME1 = Scene.v().getMethod("<java.lang.Class: java.lang.Class forName(java.lang.String)>");
	
	public BlockedJimpleBasedICFG(Set<Unit> specifiedUnits) {
		this(specifiedUnits, Collections.emptyMap(), Collections.emptyMap());
	}
	
	public BlockedJimpleBasedICFG() {
		this(new HashSet<>());
	}
	
	public BlockedJimpleBasedICFG(Set<Unit> specifiedUnits,
			Map<Pair<Unit, Unit>, Set<Unit>> intraCycles,
			Map<Unit, Set<SootMethod>> interCycles) {
		addIntraCyclesToBeBlocked(intraCycles);
		addInterCyclesToBeBlocked(interCycles);
		
		this.specifiedUnits = specifiedUnits;
		for (Unit u : this.specifiedUnits) {
			// consider reflection by the following simple heuristic
			if (containsCall(u, FORNAME1)
					|| containsCall(u, FORNAME2)
					|| containsCall(u, GETCLASS)) {
				reflectionSpecAPIs.add(CLASS_NEWINSTANCE);
				reflectionSpecAPIs.add(CONSTRUCTOR_NEWINSTANCE);
				reflectionSpecAPIs.add(INVOKE);
			}
			
			SootClass enclosingClass = getMethodOf(u).getDeclaringClass();
			if (enclosingClass.declaresMethodByName("<clinit>")) {
				SootMethod clinit = enclosingClass.getMethodByName("<clinit>");
				addCallToClinit(clinit);
			}
			
			excludeLibrary = Options.isExcludeLibrary();
		}
		Options.getBlockPackagePrefixs().forEach(this::addBlockedMethodsByPackagePrefix);
	}

	public void addIntraCyclesToBeBlocked(Map<Pair<Unit, Unit>, Set<Unit>> intraCycles) {
		intraCycles.forEach((p, exits) -> {
			Unit n = p.getO1();
			Unit d = p.getO2();
			if (!replacedSuccs.containsKey(n)) {
				replacedSuccs.put(n, new ArrayList<>(super.getSuccsOf(n)));
			}
			List<Unit> succs = replacedSuccs.get(n);
			succs.remove(d);
			succs.addAll(exits);
			removeDuplicates(succs);
			
			if (!replacedPreds.containsKey(d)) {
				replacedPreds.put(d, new ArrayList<>(super.getPredsOf(d)));
			}
			replacedPreds.get(d).remove(n);
			
			exits.forEach(exit -> {
				if (!replacedPreds.containsKey(exit)) {
					replacedPreds.put(exit, new ArrayList<>(super.getPredsOf(exit)));
				}
				replacedPreds.get(exit).add(n);
				removeDuplicates(replacedPreds.get(exit));
			});
		});
	}
	
	@Override
	public List<Unit> getPredsOf(Unit u) {
		// probable sound option is enabled
		if (replacedPreds.containsKey(u)) {
			return replacedPreds.get(u);
		}
		return super.getPredsOf(u);
	}
	
	@Override
	public List<Unit> getSuccsOf(Unit u) {
		// probable sound option is enabled
		if (replacedSuccs.containsKey(u)) {
			return replacedSuccs.get(u);
		}
		return super.getSuccsOf(u);
	}
	
	private List<Unit> getSuccsOfOwn(Unit u) {
		// Copy of getSuccsOf. We duplicate the getSuccsOf
		// to overcome the defect of Java OO mechanism.
		// probable sound option is enabled
		if (replacedSuccs.containsKey(u)) {
			return replacedSuccs.get(u);
		}
		return super.getSuccsOf(u);
	}
	
	public void addInterCyclesToBeBlocked(Map<Unit, Set<SootMethod>> interCycles) {
		interCycles.forEach((caller, callees) -> {
			if (!blockedCallees.containsKey(caller)) {
				blockedCallees.put(caller, new HashSet<>());
			}
			blockedCallees.get(caller).addAll(callees);
			
			callees.forEach(callee -> {
				if (!blockedCallers.containsKey(callee)) {
					blockedCallers.put(callee, new HashSet<>());
				}
				blockedCallers.get(callee).add(caller);
			});
		});
	}
	
	@Override
	public Collection<Unit> getCallersOf(SootMethod m) {
		if ((excludeLibrary && m.getDeclaringClass().isLibraryClass())
				|| reflectionSpecAPIs.contains(m)) {
			return Collections.emptySet();
		} else {
			Collection<Unit> callers = super.getCallersOf(m);
			if (blockedCallers.containsKey(m)) {
				callers.removeAll(blockedCallers.get(m));
			}
			
			// for dealing with unreachable <clinit>
			if (clinitCallers.containsKey(m)) {
				callers.addAll(clinitCallers.get(m));
			}
			
			// for precisely handling Thread.start
			if (threadCallers.containsKey(m)) {
				callers.addAll(threadCallers.get(m));
			}
			return callers;
		}
	}
	
	@Override
	public Collection<SootMethod> getCalleesOfCallAt(Unit u) {
		
		if (specifiedUnits.contains(u)) 
			return Collections.emptySet();
				
		Collection<SootMethod> callees = unitToCallees.getUnchecked(u);
		// we want the resolved callees of invoke/newInstance but not 
		// the invoke/newInstance() method bodies themselves
		if(!reflectionSpecAPIs.isEmpty()) {
			callees.removeAll(reflectionSpecAPIs);
		}
		// Some facts are propagated incorrectly through <clinit> in the call graph.
		// To fix this problem, we temporarily remove some <clinit> from call graph.
		callees.removeAll(alwaysBlockedMethods);
		
		// probable sound option is enabled
		if (blockedCallees.containsKey(u)) {
			callees.removeAll(blockedCallees.get(u));
		}
		
		// for dealing with unreachable clinit
		if (clinitCallees.containsKey(u)) {
			callees.addAll(clinitCallees.get(u));
		}
		
		// for precisely handling Thread.start
		if (threadCallees.containsKey(u)) {
			callees.addAll(threadCallees.get(u));
		}
		
		if (excludeLibrary) {
			// remove all calls to library
			return callees.stream()
					.filter(m -> m.getDeclaringClass().isApplicationClass())
					.collect(Collectors.toSet());
		} else {
			return callees;
		}
	}
	
	public <E> void removeDuplicates(Collection<E> collection) {
		Set<E> set = new HashSet<>(collection);
		collection.clear();
		collection.addAll(set);
	}
	
	public void addBlockedMethodsByPackagePrefix(String PackagePrefix) {
		Scene.v().getClasses().forEach(c -> {
			if (c.getPackageName().startsWith(PackagePrefix)) {
				addBlockedMethodsByClass(c);
			}
		});
	}
	
	public void addBlockedMethodsByPackage(String packageName) {
		Scene.v().getClasses().forEach(c -> {
			if (c.getPackageName().equals(packageName)) {
				addBlockedMethodsByClass(c);
			}
		});
	}
	
	public void addBlockedMethodsByClass(SootClass sootClass) {
		alwaysBlockedMethods.addAll(sootClass.getMethods());
	}
	
	public void addBlockedMethodsBySig(String methodName, Type... params) {
		Scene.v().getClasses().forEach(c -> {
			try {
				alwaysBlockedMethods.add(c.getMethod(methodName, Arrays.asList(params)));
			} catch (Exception e) {}
		});
	}
	
	public static boolean containsCall(Unit unit, SootMethod method) {
		if (unit instanceof Stmt) {
			Stmt stmt = (Stmt) unit;
			if (stmt.containsInvokeExpr()) {
				SootMethod callee = stmt.getInvokeExpr().getMethod();
				return callee.equals(method);
			}
		}
		return false;
	}
	
	protected void addCallToClinit(SootMethod clinit) {
		Collection<Unit> callers = getCallersOf(clinit);
		assert !callers.isEmpty();
		for (Unit caller : callers) {
			if (isCallStmt(caller)) {
				return;
			}
		}
		for (Unit caller : callers) {
			Stack<Unit> stack = new Stack<>();
			Set<Unit> visited = new HashSet<>();
			stack.push(caller);
			while (!stack.isEmpty()) {
				Unit u = stack.pop();
				visited.add(u);
				if (isCallStmt(u)) {
					if (!clinitCallees.containsKey(u)) {
						clinitCallees.put(u,  new HashSet<>());
					}
					clinitCallees.get(u).add(clinit);
					if (!clinitCallers.containsKey(clinit)) {
						clinitCallers.put(clinit, new HashSet<>());
					}
					clinitCallers.get(clinit).add(u);
					return;
				} else {
					getSuccsOfOwn(u).forEach(succ -> {
						if (!visited.contains(succ)) {
							stack.push(succ);
						}
					});
				}
			}
		}
		System.out.println("[addCallToClinit]: call not found for " + clinit);
	}
	
}
