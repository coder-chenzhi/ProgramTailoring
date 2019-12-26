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

import static tailor.extension.tagger.ConstructorCallTagger.getNewExpr;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import soot.Hierarchy;
import soot.Local;
import soot.PointsToAnalysis;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NewExpr;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.Node;
import soot.jimple.spark.pag.VarNode;
import soot.jimple.spark.sets.P2SetVisitor;
import soot.jimple.spark.sets.PointsToSetInternal;
import soot.toolkits.scalar.Pair;
import tailor.Debug;
import tailor.Options;
import tailor.icfg.BlockedJimpleBasedICFG;
import tailor.icfg.ICFG;
import tailor.ifds.StatementSequence;
import tailor.ifds.StatementSequenceUtil;
import tailor.tag.BranchTag;
import tailor.tag.ConstructorCallTag;
import tailor.tag.InterCycleTag;
import tailor.tag.IntraCycleTag;

public class ExtensionFinder {
	
	private tailor.icfg.ICFG<Unit, SootMethod> icfg;
	private StatementSequenceUtil util;
	private Set<SootMethod> reachable;
	
	private Map<NewExpr, Unit> unitOfNewExpr;
	private Map<Local, Unit> unitOfNewInstance;
	
	private Map<Unit, Set<Unit>> extensionPreds;
	private Map<Unit, Set<Unit>> extensionSuccs;
	
	private PointsToAnalysis pta;
	private AllocGetter allocGetter = new AllocGetter();

	private Map<SootMethod, Boolean> methodToBranchOrVirtualCall = new HashMap<>();
	
	public ExtensionFinder(ICFG<Unit, SootMethod> icfg,
			StatementSequenceUtil util) {
		this.icfg = icfg;
		this.util = util;
		this.pta = Scene.v().getPointsToAnalysis();
		findUnitOfAllocationSite();
		computeReachableMethods();
	}
	
	public Map<Unit, Set<Unit>> find(Set<StatementSequence> facts) {
		Set<Unit> heads = facts.stream()
				.map(f -> f.getHead())
				.collect(Collectors.toSet());
		
		buildExtensionMap(heads);
		removeUselessUnits();
		//printNExtendedRelation(extensionPreds, heads, 0);
		return extensionPreds;
	}
	
	/*
	 * Print first n extension.
	 */
	@SuppressWarnings("unused")
	private void printNExtendedRelation(Map<Unit, Set<Unit>> extension,
			Set<Unit> heads, int n) {
		Stack<Unit> stack = new Stack<>();
		Map<Unit, Set<LinkedList<Unit>>> seqs = new HashMap<>();
		heads.forEach(head -> {
			LinkedList<Unit> headSeq = new LinkedList<>();
			headSeq.add(head);
			seqs.put(head, Collections.singleton(headSeq));
			stack.push(head);
		});
		int i = 0;
		while (!stack.isEmpty() && i < n) {
			Unit unit = stack.pop();
			SootMethod inMethod = icfg.getMethodOf(unit);
			if (inMethod.isMain()) {
				for (LinkedList<Unit> seq : seqs.get(unit)) {
					System.out.println("[Ext-rela]: " + (1 + seq.size()));
					System.out.print("  > ");
					System.out.println(
							IFDSExtensionBottomUpTailor.unitToString(unit, inMethod));
					for (Unit u : seq) {
						System.out.print("  > ");
						System.out.println(
								IFDSExtensionBottomUpTailor.unitToString(u, icfg.getMethodOf(u)));
						++i;
					}
				}
			} else {
				extension.get(unit).forEach(ext -> {
					if (ext != unit) {
						if (!seqs.containsKey(ext)) {
							seqs.put(ext, new HashSet<>());
						}
						boolean canPush = true;
						for (LinkedList<Unit> seq : seqs.get(unit)) {
							if (seq.contains(ext)) {
								canPush = false;
							} else {
								LinkedList<Unit> extSeq = new LinkedList<>(seq);
								extSeq.addFirst(unit);
								seqs.get(ext).add(extSeq);
							}
						}
						if (canPush) {
							stack.push(ext);
						}
					}
				});
			}
		}
	}

	private void findUnitOfAllocationSite() {
		unitOfNewExpr = new HashMap<NewExpr, Unit>();
		unitOfNewInstance = new HashMap<Local, Unit>();
		icfg.allNodes().forEach(unit -> {
			NewExpr newExpr = getNewExpr(unit);
			if (newExpr != null) {
				unitOfNewExpr.put(newExpr, unit);
			} else if (unit instanceof Stmt) {
				Stmt stmt = (Stmt) unit;
				if (stmt.containsInvokeExpr()
						&& stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
					InstanceInvokeExpr iie = (InstanceInvokeExpr) stmt.getInvokeExpr();
					SootMethod callee = iie.getMethod();
					if (callee.equals(BlockedJimpleBasedICFG.CONSTRUCTOR_NEWINSTANCE)
							|| callee.equals(BlockedJimpleBasedICFG.CLASS_NEWINSTANCE)) {
						unitOfNewInstance.put((Local) iie.getBase(), unit);
					}
				}
			}
		});
	}
	
	private void computeReachableMethods() {
		SootMethod main = Scene.v().getMainMethod();
		Queue<SootMethod> q = new LinkedList<>();
		q.add(main);
		reachable = new HashSet<>();
		while (!q.isEmpty()) {
			SootMethod m = q.poll();
			if (reachable.add(m)) {
				icfg.getCallsFromWithin(m)
					.stream()
					.map(icfg::getCalleesOfCallAt)
					.forEach(q::addAll);
			}
		}
	}
	
	private void buildExtensionMap(Set<Unit> heads) {
		extensionPreds = new HashMap<>();
		extensionSuccs = new HashMap<>();
		Queue<Unit> queue = new LinkedList<>(heads);
		while (!queue.isEmpty()) {
			Unit unit = queue.poll();
			if (!extensionPreds.containsKey(unit)) {
				extensionPreds.put(unit, findExtensionUnits(unit));
				extensionPreds.get(unit).forEach(ext -> {
					if (!extensionPreds.containsKey(ext)) {
						queue.add(ext);
					}
					if (!extensionSuccs.containsKey(ext)) {
						extensionSuccs.put(ext, new HashSet<>());
					}
					extensionSuccs.get(ext).add(unit);
					Debug.println("[Add extension]: " + Debug.unitToString(unit, icfg));
					Debug.println("    -> " + Debug.unitToString(ext, icfg));
				});
			}
		}
	}
	
	private Set<Unit> findExtensionUnits(Unit unit) {
		SootMethod inMethod = icfg.getMethodOf(unit);
		Set<Unit> res = new HashSet<>();
		if (inMethod.isStatic()) {
			icfg.getCallersOf(inMethod)
				.stream()
				.filter(icfg::isCallStmt)
				.forEach(res::add);
		} else {
			allocGetter.setOutput(res);
			icfg.getCallersOf(inMethod).forEach(caller -> {
				Stmt stmt = (Stmt) caller;
				if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
					InstanceInvokeExpr invokeExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
					SootMethod callee = invokeExpr.getMethod();
					Local base = null;
					if (callee.equals(BlockedJimpleBasedICFG.INVOKE)) {
						Value recv = invokeExpr.getArg(0);
						if (recv instanceof Local) {
							base = (Local) recv;
						} else {
							Debug.println("[Ext-rela]: invalid call: "
									+ Debug.unitToString(caller, icfg)
									+ " -> "
									+ inMethod
									+ ": " + unit);
						}
					} else if (callee.equals(BlockedJimpleBasedICFG.CONSTRUCTOR_NEWINSTANCE)
							|| callee.equals(BlockedJimpleBasedICFG.CLASS_NEWINSTANCE)) {
						if (stmt instanceof AssignStmt) {
							AssignStmt assign = (AssignStmt) stmt;
							base = (Local) assign.getLeftOp();
						}
					} else {
						base = (Local) invokeExpr.getBase();
					}
					if (base != null) {
						if (pta.reachingObjects(base) instanceof PointsToSetInternal) {
							PointsToSetInternal pts =
									(PointsToSetInternal) pta.reachingObjects(base);
							allocGetter.setInvokeExpr(invokeExpr);
							allocGetter.setTarget(inMethod);
							pts.forall(allocGetter);
						} else {
							throw new RuntimeException("Unexpected pointer analysis: " + pta);
						}
					}
				} else {
					// There is one case that instance method will be invoked
					// by a static call site (in SPARK call graph):
					// AccessController.doPrivileged -> PrivilegedAction.run()
					res.add(caller);
				}
			});
		}
		res.remove(unit); // remove self-cycle
		// remove extension point whose number of allocation sites is 
		// larger than the threshold.
		// may sacrifice precision but performance could be improved
//		if (res.size() > 10) {
//			return Collections.emptySet();
//		} else {
//			return res;
//		}
		if (Options.isExcludeLibraryExtension()) {
			for (Unit ext : res) {
				if (icfg.getMethodOf(ext).getDeclaringClass().isLibraryClass()) {
					res.clear();
					break;
				}
			}
		}
		return res;
	}
	
	private void removeUselessUnits() {
		Set<Unit> units = new HashSet<>(extensionPreds.keySet());
		units.stream()
			.filter(this::isUselessUnit)
			.forEach(this::removeUnitFromExtensionMap);
	}
	
	private void removeUnitFromExtensionMap(Unit unit) {
		Debug.println("[Useless]: " + unit);
		Set<Unit> predsOfUnit = extensionPreds.get(unit);
		Set<Unit> succsOfUnit = extensionSuccs.get(unit);
		succsOfUnit.forEach(succ -> {
			Set<Unit> predsOfSucc = extensionPreds.get(succ);
			predsOfSucc.remove(unit);
			predsOfUnit.forEach(predOfUnit -> {
				if (predOfUnit != succ) {
					predsOfSucc.add(predOfUnit);
				}
			});
		});
		predsOfUnit.forEach(pred -> {
			Set<Unit> succsOfPred = extensionSuccs.get(pred);
			succsOfPred.remove(unit);
			succsOfUnit.forEach(succOfUnit -> {
				if (succOfUnit != pred) {
					succsOfPred.add(succOfUnit);
				}
			});
		});
		extensionPreds.remove(unit);
		extensionSuccs.remove(unit);
	}
	
	private boolean isUselessUnit(Unit unit) {
		SootMethod inMethod = icfg.getMethodOf(unit);
		if (util.isUnitOfSC(unit) || inMethod.isMain()) {
			return false;
		}
		
		// Filter out the unit in a loop or in the method which is in a cycle
		if (unit.hasTag(IntraCycleTag.NAME)
				|| inMethod.hasTag(IntraCycleTag.NAME)
				|| inMethod.hasTag(InterCycleTag.NAME)) {
			return true;
		}
		
		if (isClass$Call(unit) || !reachable.contains(inMethod)) {
			return true;
		}
		
		return !isInBranchOrPolymorphicVirtualCall(unit);
	}
	
	private boolean isInBranch(Unit unit) {
		return unit.hasTag(BranchTag.NAME);
	}
	
	private boolean isInPolymorphicVirtualCall(SootMethod inMethod) {
		// check whether the `u` statement is in method
		// called from a virtual call
		for (Unit caller : icfg.getCallersOf(inMethod)) {
			// May lead the extended sequence imprecise (longer than it needs)
			if (caller instanceof Stmt) {
				Stmt stmt = (Stmt) caller;
				if (stmt.containsInvokeExpr()) {
					InvokeExpr invokeExpr = stmt.getInvokeExpr();
					if (invokeExpr instanceof InterfaceInvokeExpr
							|| invokeExpr instanceof VirtualInvokeExpr) {
						if (icfg.getCalleesOfCallAt(caller)
								.stream()
								.filter(m -> !m.getName().equals("<clinit>"))
								.count() > 1) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	private boolean isInBranchOrPolymorphicVirtualCall(SootMethod method) {
		// An unit within recursion will not be considered as a potential 
		// extension point, hence this implementation will not stuck
		// in an infinite loop.
		if (!methodToBranchOrVirtualCall.containsKey(method)) {
			boolean result = false;
			if (isInPolymorphicVirtualCall(method)) {
				result = true;
			} else {
				for (Unit caller : icfg.getCallersOf(method)) {
					// Soot treats some normal statements as caller of <clinit>
					if (icfg.isCallStmt(caller)
							&& isInBranchOrPolymorphicVirtualCall(caller)) {
						result = true;
						break;
					}
				}
			}
			methodToBranchOrVirtualCall.put(method, result);
		}
		return methodToBranchOrVirtualCall.get(method);
	}
	
	private boolean isInBranchOrPolymorphicVirtualCall(Unit unit) {
		if (isInBranch(unit)) {
			return true;
		} else {
			return isInBranchOrPolymorphicVirtualCall(icfg.getMethodOf(unit));
		}
	}

	private boolean isClass$Call(Unit u) {
		if (u instanceof Stmt) {
			Stmt stmt = (Stmt) u;
			if (stmt.containsInvokeExpr()) {
				return stmt.getInvokeExpr().getMethod().getName().equals("class$");
			}
		}
		return false;
	}
	
	class AllocGetter extends P2SetVisitor {
		
		private Hierarchy hierarchy = Scene.v().getActiveHierarchy();
		private InvokeExpr invokeExpr;
		private SootMethod target;
		private Set<Unit> output;
		
		public void setInvokeExpr(InvokeExpr invokeExpr) {
			this.invokeExpr = invokeExpr;
		}
		
		public void setTarget(SootMethod target) {
			this.target = target;
		}
		
		public void setOutput(Set<Unit> allocSet) {
			this.output = allocSet;
		}
		
		@Override
		public void visit(Node n) {
			AllocNode allocNode = (AllocNode) n;
			Object alloc = allocNode.getNewExpr();
			if (alloc instanceof NewExpr) {
				NewExpr newExpr = (NewExpr) alloc;
				Unit newStmt = unitOfNewExpr.get(newExpr);
				ConstructorCallTag consTag =
						(ConstructorCallTag) newStmt.getTag(ConstructorCallTag.NAME);
				Unit consCall = consTag.getConstructorCall();
				SootClass newClass = ((Stmt) consCall)
						.getInvokeExpr()
						.getMethod()
						.getDeclaringClass();
				if (isPossibleClass(newClass)) {
					output.add(consCall);
				}
			} else if (alloc instanceof Pair) {
				Pair<?, ?> p = (Pair<?, ?>) alloc;
				if (p.getO1() instanceof VarNode && p.getO2() instanceof SootClass) {
					// Handle newInstance()
					VarNode vn = (VarNode) p.getO1();
					SootClass newClass = (SootClass) p.getO2();
					if (vn.getVariable() instanceof Local) {
						Local base = (Local) vn.getVariable();
						Unit consCall = unitOfNewInstance.get(base);
						if (isPossibleClass(newClass) && consCall != null) {
							output.add(consCall);
						}
					}
				}
			} else {
//				System.out.println("Unknown allocation site: " + n);
//				throw new RuntimeException("Unknown allocation site: " + n);
			}
		}
		
		private boolean isPossibleClass(SootClass sootClass) {
			if (invokeExpr instanceof SpecialInvokeExpr	|| target.isConstructor()) {
				// handle constructor call, private call and super call
				return isClassSubclassOfIncluding(sootClass, target.getDeclaringClass());
			} else {
				SootMethod callee = invokeExpr.getMethod();
				SootClass declClass = callee.getDeclaringClass();
				boolean isSubtype;
				if (declClass.isInterface()) {
					isSubtype = hierarchy.getImplementersOf(declClass).contains(sootClass);
				} else {
					isSubtype = isClassSubclassOfIncluding(sootClass, declClass);
				}
				return isSubtype
						&& hierarchy
							.resolveConcreteDispatch(sootClass, invokeExpr.getMethod())
							.equals(target);
			}
		}
		
		/**
		 * The implementation of Hierarchy.isClassSubclassOfIncluding() provided by
		 * Soot acts like this: "If one of the known parent classes is phantom, we
		 * conservatively assume that the current class might be a child". And this
 		 * will cause exception from AllocGetter.isPossibleClass. 
		 * This substitution version which removes the above assumption is created
		 * to fix the exception.
		 */
		private boolean isClassSubclassOfIncluding(SootClass child, SootClass possibleParent) {
	        child.checkLevel(SootClass.HIERARCHY);
	        possibleParent.checkLevel(SootClass.HIERARCHY);
	        List<SootClass> parentClasses = hierarchy.getSuperclassesOfIncluding(child);
	        return parentClasses.contains(possibleParent);
	    }
	}
}
