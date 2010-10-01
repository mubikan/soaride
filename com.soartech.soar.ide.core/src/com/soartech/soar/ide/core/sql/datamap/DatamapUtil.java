package com.soartech.soar.ide.core.sql.datamap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import com.soartech.soar.ide.core.sql.ISoarDatabaseTreeItem;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow;
import com.soartech.soar.ide.core.sql.TraversalUtil;
import com.soartech.soar.ide.core.sql.Triple;
import com.soartech.soar.ide.core.sql.SoarDatabaseRow.Table;

public class DatamapUtil {
	
	@SuppressWarnings("unchecked")
	public static ArrayList<DatamapInconsistency> getInconsistancies(SoarDatabaseRow rule) {
		ArrayList<Triple> triples = TraversalUtil.getTriplesForRule(rule);
		HashMap<String, ArrayList<Triple>> triplesByVariable = TraversalUtil.triplesWithVariable(triples);
		ArrayList<Triple> stateTriples = new ArrayList<Triple>();
		for (Triple triple : triples) {
			if (triple.hasState) {
				stateTriples.add(triple);
			}
		}
		ArrayList<DatamapInconsistency> ret = new ArrayList<DatamapInconsistency>();
		ArrayList<SoarDatabaseRow> problemSpaces = rule.getDirectedJoinedParentsOfType(Table.PROBLEM_SPACES);
		HashSet<Triple> visitedTriples = new HashSet<Triple>();
		for (SoarDatabaseRow problemSpace : problemSpaces) {
			ArrayList<SoarDatabaseRow> roots = new ArrayList<SoarDatabaseRow>();
			roots.addAll((Collection<? extends SoarDatabaseRow>) problemSpace.getChildrenOfType(Table.DATAMAP_IDENTIFIERS).get(0).getDirectedJoinedChildren(false));
			for (Triple stateTriple : stateTriples) {
				getInconsistencies(rule, problemSpace, stateTriple, roots, triplesByVariable, visitedTriples, ret);
			}
		}
		return ret;
	}
	
	/**
	 * Recursively build a list of inconsistencies between a set of triples and a datamap.
	 * @param rule The current rule.
	 * @param problemSpace The current problem space.
	 * @param currentTriple The current triple (begin with triples that have state).
	 * @param currentNodes  The current datamap nodes (begin with children of root state).
	 * @param triplesByVariable Maps variable names onto triples.
	 * @param visitedNodes Triples that have been visited so far.
	 * @param inconsistencies Output variable.
	 */
	@SuppressWarnings("unchecked")
	private static void getInconsistencies(SoarDatabaseRow rule,
			SoarDatabaseRow problemSpace,
			Triple currentTriple, ArrayList<SoarDatabaseRow> currentNodes,
			HashMap<String, ArrayList<Triple>> triplesByVariable,
			HashSet<Triple> visitedTriples,
			ArrayList<DatamapInconsistency> inconsistencies) {
		
		// Avoid infinite recursion
		if (visitedTriples.contains(currentTriple)) return;
		visitedTriples.add(currentTriple);
		
		// Find datamap nodes whose attribute and value type match the attribute and value type of the current triple.
		// Also, find string and enumeration nodes for triples whose values are constant.
		// Also, find enumeration nodes with a correct value for triples whose values are cosntant.
		// If no nodes are found, add an error to the list.
		ArrayList<SoarDatabaseRow> matchingNodes = new ArrayList<SoarDatabaseRow>();
		
		HashSet<Table> matchingTypes = new HashSet<Table>();
		
		if (currentTriple.valueIsVariable()) matchingTypes.add(Table.DATAMAP_IDENTIFIERS);
		else if (currentTriple.valueIsInteger()) matchingTypes.add(Table.DATAMAP_INTEGERS);
		else if (currentTriple.valueIsFloat()) matchingTypes.add(Table.DATAMAP_FLOATS);
		if (currentTriple.valueIsConstant()) {
			matchingTypes.add(Table.DATAMAP_STRINGS);
			matchingTypes.add(Table.DATAMAP_ENUMERATIONS);
		}
		
		for (SoarDatabaseRow node : currentNodes) {
			if (node.getName().equals(currentTriple.attribute) &&
					matchingTypes.contains(node.getTable())) {
				matchingNodes.add(node);
			}
		}
		
		if (matchingNodes.size() == 0) {
			inconsistencies.add(new DatamapInconsistency(rule, problemSpace, "Invalid attribute: " + currentTriple.attribute, currentTriple.attributeOffset));
			return;
		}
		
		// Make sure the value of the triple is a valid value in at least one of the matching datamap nodes.
		// Otherwise, add an error.
		if (currentTriple.valueIsConstant()) {
			boolean match = false;
			for (SoarDatabaseRow node : matchingNodes) {
				if (node.getTable() == Table.DATAMAP_STRINGS) {
					match = true;
					break;
				}
				else if (node.getTable() == Table.DATAMAP_ENUMERATIONS) {
					for (SoarDatabaseRow enumValue : node.getChildrenOfType(Table.DATAMAP_ENUMERATION_VALUES)) {
						if (enumValue.getName().equals(currentTriple.value)) {
							match = true;
							break;
						}
					}
					if (match) break;
				}
				else if (node.getTable() == Table.DATAMAP_INTEGERS) {
					int tripleValue = Integer.parseInt(currentTriple.value);
					int min = (Integer) node.getColumnValue("min_value");
					int max = (Integer) node.getColumnValue("max_value");
					if (tripleValue >= min && tripleValue <= max) {
						match = true;
						break;
					}
				}
				else if (node.getTable() == Table.DATAMAP_FLOATS) {
					float tripleValue = Float.parseFloat(currentTriple.value);
					float min = (Float) node.getColumnValue("min_value");
					float max = (Float) node.getColumnValue("max_value");
					if (tripleValue >= min && tripleValue <= max) {
						match = true;
						break;
					}
				}
			}
			if (!match) {
				inconsistencies.add(new DatamapInconsistency(rule, problemSpace, "Invalid value: " + currentTriple.value, currentTriple.valueOffset));
				return;
			}
		}
		
		// If the value of the current triple is an identifier, recursively check chidlren of this triple.
		if (currentTriple.valueIsVariable()) {
			ArrayList<SoarDatabaseRow> childNodes = new ArrayList<SoarDatabaseRow>();
			for (SoarDatabaseRow matchingNode : matchingNodes) {
				childNodes.addAll((Collection<? extends SoarDatabaseRow>) matchingNode.getDirectedJoinedChildren(false));
			}
			ArrayList<Triple> triplesWithVariable = triplesByVariable.get(currentTriple.value);
			if (triplesWithVariable != null) {
				for (Triple child : triplesWithVariable) {
					getInconsistencies(rule, problemSpace, child, childNodes, triplesByVariable, visitedTriples, inconsistencies);
				}
			}
		}
	}
	
	public static ArrayList<Correction> getCorrections(SoarDatabaseRow problemSpace, ArrayList<Triple> triples, SoarDatabaseRow root) {
		ArrayList<TerminalPath> paths = terminalPathsForTriples(triples);
		ArrayList<Correction> corrections = new ArrayList<Correction>();
		
		// Iterate over all paths to build corrections
		//long buildCorrectionsStart = new Date().getTime();
		for (TerminalPath terminalPath : paths) {
			ArrayList<Triple> path = terminalPath.path;
			ArrayList<SoarDatabaseRow> currentNodes = new ArrayList<SoarDatabaseRow>();
			currentNodes.add(root);
			
			// Walk down the path, keeping track of which datamap nodes
			// correspond to the current location on the path.
			for (int i = 0; i < path.size(); ++i) {
				
				// Children of the current datamap node that match the current triple.
				ArrayList<SoarDatabaseRow> childNodes = new ArrayList<SoarDatabaseRow>();
				for (SoarDatabaseRow node : currentNodes) {
					
					// Children of the current datamap node that are of the same type as the value of the current triple.
					ArrayList<ISoarDatabaseTreeItem> childNodesWhoseNamesMightNotMatch = new ArrayList<ISoarDatabaseTreeItem>();
					
					Triple triple = path.get(i);
					if (triple.valueIsVariable()) {
						childNodesWhoseNamesMightNotMatch.addAll(node.getDirectedJoinedChildrenOfType(Table.DATAMAP_IDENTIFIERS, false, false));
					} else if (triple.valueIsInteger()) {
						childNodesWhoseNamesMightNotMatch.addAll(node.getDirectedJoinedChildrenOfType(Table.DATAMAP_INTEGERS, false, false));
					} else if (triple.valueIsFloat()) {
						childNodesWhoseNamesMightNotMatch.addAll(node.getDirectedJoinedChildrenOfType(Table.DATAMAP_FLOATS, false, false));
					} else if (triple.valueIsString()) {
						// Only add enums if they have a value that's correct
						ArrayList<ISoarDatabaseTreeItem> enumItems = node.getDirectedJoinedChildrenOfType(Table.DATAMAP_ENUMERATIONS, false, false);
						for (ISoarDatabaseTreeItem enumItem : enumItems) {
							SoarDatabaseRow enumRow = (SoarDatabaseRow) enumItem;
							ArrayList<SoarDatabaseRow> enumValues = enumRow.getChildrenOfType(Table.DATAMAP_ENUMERATION_VALUES);
							for (SoarDatabaseRow enumValue : enumValues) {
								if (enumValue.getName().equals(triple.value)) {
									childNodesWhoseNamesMightNotMatch.add(enumItem);
								}
							}
						}
					}
					// Always add strings.
					childNodesWhoseNamesMightNotMatch.addAll(node.getDirectedJoinedChildrenOfType(Table.DATAMAP_STRINGS, false, false));
					
					// Filter 'items', selecting only those whose name matches the attribute of the current triple.
					for (ISoarDatabaseTreeItem item : childNodesWhoseNamesMightNotMatch) {
						SoarDatabaseRow childRow = (SoarDatabaseRow) item;
						if (childRow.getName().equals(triple.attribute)) {
							childNodes.add(childRow);
						}
					}
				}
				
				// if currentNodes.size == 0 and there's more triples,
				// propose adding triples and continue to next path
				if (childNodes.size() == 0) {
					
					// TODO debug
					// System.out.println("No more child nodes.");
					
					for (SoarDatabaseRow leafNode : currentNodes) {
						
						// TODO debug
						// System.out.println("Leaf: " + leafNode);
						
						ArrayList<Triple> addition = new ArrayList<Triple>();
						for (int j = i; j < path.size(); ++j) {
							addition.add(path.get(j));
						}
						if (addition.size() > 0) {
							Correction correction = new Correction(leafNode, addition, terminalPath.links);
							corrections.add(correction);
						}
					}
					break;
				}
				currentNodes = childNodes;
			}
		}
		return corrections;
	}
	
	private static ArrayList<TerminalPath> terminalPathsForTriples(ArrayList<Triple> triples) {
		ArrayList<TerminalPath> ret = new ArrayList<TerminalPath>();
		HashSet<String> usedVariables = new HashSet<String>();
		
		boolean grew = true;
		while (grew) {
			grew = false;
			ArrayList<Triple> nextTriples = new ArrayList<Triple>();
			
			// Iterate over all triples.
			for (Triple triple : triples) {
				
				// Keep track of whether any path from this triple was added to 'ret'.
				boolean added = false;
				
				// If the paths from this triple are naturally terminal --
				// if the triple has no child triples.
				boolean terminal = !triple.valueIsVariable() || triple.childTriples == null;
				
				// Iterate over each path from this triple.
				for (ArrayList<Triple> path : triple.getTriplePathsFromState()) {
					
					// Figure out if the path loops onto itself
					// e.g. (<s> ^attr <v1>),(<v1> ^attr <v2>),(<v2> ^attr <v3>),(<v3> ^attr <v1>)
					// In the example, this triple is the last one and its value loops back to the
					// previous reference to the variable <v1>.
					HashSet<String> pathVariables = new HashSet<String>();
					addVariablesToHashSet(path, pathVariables);
					boolean loops = triple.valueIsVariable() && pathVariables.contains(triple.value);
					
					// Also figure out if this path loops onto any path in 'ret'.
					// 'Loops' in this context is used lightly -- there may not be any
					// cyclical paths.
					ArrayList<Triple> links = new ArrayList<Triple>();
					boolean loopsIntoPath = false;
					for (TerminalPath retPath : ret) {
						Triple t = pathLoopsIntoPath(path, retPath.path);
						if (t != null) {
							links.add(t);
							loopsIntoPath = true;
							// break;
						}
					}
					
					if (terminal || loops || loopsIntoPath) {

						// Make sure the path isn't too long
						boolean tooLong = false;
						for (TerminalPath retPath : ret) {
							if (pathCollidesWithPath(path, retPath.path)) {
								tooLong = true;
								break;
							}
						}
						
						// Make sure the path isn't identical to a path that's already been proposed.
						boolean identical = false;
						for (TerminalPath retPath : ret) {
							if (path.equals(retPath.path) || pathsAreRedundant(path, retPath.path)) {
								identical = true;
								break;
							}
						}
						
						if (!tooLong && !identical) {
							grew = true;
							added = true;
							TerminalPath newPath = new TerminalPath(path, links);
							ret.add(newPath);
							addVariablesToHashSet(path, usedVariables);
							
							// TODO debug
							/*
							System.out.println("Added path: " + path);
							System.out.println("terminal: " + terminal);
							System.out.println("loops: " + loops);
							System.out.println("loopsIntoPath: " + loopsIntoPath + '\n');
							*/
						}
					}
				}
				if (!added) nextTriples.add(triple);
			}
			triples = nextTriples;
		}
		
		return ret;
	}
	
	private static boolean pathsAreRedundant(ArrayList<Triple> path, ArrayList<Triple> retPath) {
		int len = Math.min(path.size(), retPath.size());
		for (int i = 0; i < len; ++i) {
			if (!path.get(i).equals(retPath.get(i))) {
				return false;
			}
		}
		return true;
	}

	private static void addVariablesToHashSet(ArrayList<Triple> triples, HashSet<String> variables) {
		for (Triple triple : triples) {
			variables.add(triple.variable);
		}
	}
	
	/**
	 * 
	 * @param path
	 * @param retPath
	 * @return True if the two paths diverge and than converge again.
	 */
	private static boolean pathCollidesWithPath(ArrayList<Triple> path, ArrayList<Triple> retPath) {
		int index = 0;
		for ( ; index < path.size() - 1; ++index) {
			if (index >= retPath.size()) {
				return false;
			}
			if (!path.get(index).equals(retPath.get(index))) {
				break;
			}
		}
		if (index == path.size() - 1) {
			return false;
		}
		
		HashSet<String> retPathVariables = new HashSet<String>();
		for (Triple t : retPath) {
			retPathVariables.add(t.variable);
		}
		
		for ( ; index < path.size() - 1; ++index) {
			/*
			if (index >= retPath.size()) {
				return false;
			}
			*/
			if (retPathVariables.contains(path.get(index).value)) {
				return true;
			}
		}
		
		return false;
	}
	
	private static Triple pathLoopsIntoPath(ArrayList<Triple> path, ArrayList<Triple> retPath) {
		Triple last = path.get(path.size() - 1);
		if (!last.valueIsVariable()) return null;
		for (int i = 0; i < retPath.size(); ++i) {
			Triple retTriple = retPath.get(i);			
			if (last.value.equals(retTriple.value)) {
				if (!retTriple.equals(last)) {
					return retTriple;
				}
			}
			
		}
		return null;
	}
}
