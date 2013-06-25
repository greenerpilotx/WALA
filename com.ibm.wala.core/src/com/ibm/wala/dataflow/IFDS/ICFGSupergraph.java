/*******************************************************************************
 * Licensed Materials - Property of IBM
 * 
 * "Restricted Materials of IBM"
 *
 * Copyright (c) 2008 IBM Corporation.
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.dataflow.IFDS;

import java.util.Iterator;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.intset.IntSet;

/**
 * Forward supergraph induced over an {@link ExplodedInterproceduralCFG}
 * 
 * This should lazily build the supergraph as it is explored.
 * 
 * @author sjfink
 * 
 */
public class ICFGSupergraph implements ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> {

  private final AnalysisCache analysisCache;
  
  private final AnalysisOptions options = new AnalysisOptions();

  private final ExplodedInterproceduralCFG icfg;

  protected ICFGSupergraph(ExplodedInterproceduralCFG icfg, AnalysisCache cache) {
    this.icfg = icfg;
    this.analysisCache = cache;
  }

  public static ICFGSupergraph make(CallGraph cg, AnalysisCache cache) {
    ICFGSupergraph w = new ICFGSupergraph(ExplodedInterproceduralCFG.make(cg), cache);
    return w;
  }

  @Override
  public Graph<? extends CGNode> getProcedureGraph() {
    return icfg.getCallGraph();
  }

  public IClassHierarchy getClassHierarchy() {
    return icfg.getCallGraph().getClassHierarchy();
  }

  public IR getIR(IMethod m, Context c) {
    //AnalysisOptions options = new AnalysisOptions();
    IR ir = analysisCache.getSSACache().findOrCreateIR(m, c, options.getSSAOptions());
    return ir;
  }

  public IR getIR(CGNode n) {
    //AnalysisOptions options = new AnalysisOptions();
    IR ir = analysisCache.getSSACache().findOrCreateIR(n.getMethod(), n.getContext(), options.getSSAOptions());
    return ir;
  }

  @Override
  public byte classifyEdge(BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dest) {
    if (isCall(src)) {
      if (isEntry(dest)) {
        return CALL_EDGE;
      } else {
        return CALL_TO_RETURN_EDGE;
      }
    } else if (isExit(src)) {
      return RETURN_EDGE;
    } else {
      return OTHER;
    }
  }

  /*
   * @see com.ibm.wala.dataflow.IFDS.ISupergraph#getCallSites(java.lang.Object,
   * java.lang.Object)
   */
  @Override
  public Iterator<BasicBlockInContext<IExplodedBasicBlock>> getCallSites(BasicBlockInContext<IExplodedBasicBlock> r, CGNode callee) {
    return icfg.getCallSites(r, callee);
  }

  @Override
  public Iterator<? extends BasicBlockInContext<IExplodedBasicBlock>> getCalledNodes(BasicBlockInContext<IExplodedBasicBlock> call) {
    final Filter<BasicBlockInContext<IExplodedBasicBlock>> isEntryFilter = new Filter<BasicBlockInContext<IExplodedBasicBlock>>() {
      @Override
      public boolean accepts(BasicBlockInContext<IExplodedBasicBlock> o) {
        return o.isEntryBlock();
      }
    };
    return new FilterIterator<BasicBlockInContext<IExplodedBasicBlock>>(getSuccNodes(call), isEntryFilter);
  }

  @Override
  @SuppressWarnings("unchecked")
  public BasicBlockInContext<IExplodedBasicBlock>[] getEntriesForProcedure(CGNode procedure) {
    return new BasicBlockInContext[] { icfg.getEntry(procedure) };
  }

  @Override
  @SuppressWarnings("unchecked")
  public BasicBlockInContext<IExplodedBasicBlock>[] getExitsForProcedure(CGNode procedure) {
    return new BasicBlockInContext[] { icfg.getExit(procedure) };
  }

  @Override
  public BasicBlockInContext<IExplodedBasicBlock> getLocalBlock(CGNode procedure, int i) {
    IExplodedBasicBlock b = icfg.getCFG(procedure).getNode(i);
    return new BasicBlockInContext<IExplodedBasicBlock>(procedure, b);
  }

  @Override
  public int getLocalBlockNumber(BasicBlockInContext<IExplodedBasicBlock> n) {
    return n.getDelegate().getNumber();
  }


  public BasicBlockInContext<IExplodedBasicBlock> getMainEntry() {
    Assertions.UNREACHABLE();
    return null;
  }

  @Override
  public Iterator<BasicBlockInContext<IExplodedBasicBlock>> getNormalSuccessors(BasicBlockInContext<IExplodedBasicBlock> call) {
    return EmptyIterator.instance();
  }

  @Override
  public int getNumberOfBlocks(CGNode procedure) {
    Assertions.UNREACHABLE();
    return 0;
  }

  @Override
  public CGNode getProcOf(BasicBlockInContext<IExplodedBasicBlock> n) {
    return icfg.getCGNode(n);
  }


  @Override
  public Iterator<? extends BasicBlockInContext<IExplodedBasicBlock>> getReturnSites(BasicBlockInContext<IExplodedBasicBlock> call,
      CGNode callee) {
    return icfg.getReturnSites(call);
  }

  @Override
  public boolean isCall(BasicBlockInContext<IExplodedBasicBlock> n) {
    return n.getDelegate().getInstruction() instanceof SSAAbstractInvokeInstruction;
  }

  @Override
  public boolean isEntry(BasicBlockInContext<IExplodedBasicBlock> n) {
    return n.getDelegate().isEntryBlock();
  }

  @Override
  public boolean isExit(BasicBlockInContext<IExplodedBasicBlock> n) {
    return n.getDelegate().isExitBlock();
  }

  @Override
  public boolean isReturn(BasicBlockInContext<IExplodedBasicBlock> n) {
    return icfg.isReturn(n);
  }

  @Override
  public void removeNodeAndEdges(BasicBlockInContext<IExplodedBasicBlock> N) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();

  }

  @Override
  public void addNode(BasicBlockInContext<IExplodedBasicBlock> n) {
    Assertions.UNREACHABLE();

  }

  @Override
  public boolean containsNode(BasicBlockInContext<IExplodedBasicBlock> N) {
    return icfg.containsNode(N);
  }

  @Override
  public int getNumberOfNodes() {
    return icfg.getNumberOfNodes();
  }

  @Override
  public Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator() {
    return icfg.iterator();
  }

  @Override
  public void removeNode(BasicBlockInContext<IExplodedBasicBlock> n) {
    Assertions.UNREACHABLE();

  }

  @Override
  public void addEdge(BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dst) {
    Assertions.UNREACHABLE();

  }

  @Override
  public int getPredNodeCount(BasicBlockInContext<IExplodedBasicBlock> N) {
    return icfg.getPredNodeCount(N);
  }

  @Override
  public Iterator<BasicBlockInContext<IExplodedBasicBlock>> getPredNodes(BasicBlockInContext<IExplodedBasicBlock> N) {
    return icfg.getPredNodes(N);
  }

  @Override
  public int getSuccNodeCount(BasicBlockInContext<IExplodedBasicBlock> N) {
    return icfg.getSuccNodeCount(N);
  }

  @Override
  public Iterator<BasicBlockInContext<IExplodedBasicBlock>> getSuccNodes(BasicBlockInContext<IExplodedBasicBlock> N) {
    return icfg.getSuccNodes(N);
  }

  @Override
  public boolean hasEdge(BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dst) {
    return icfg.hasEdge(src, dst);
  }

  @Override
  public void removeAllIncidentEdges(BasicBlockInContext<IExplodedBasicBlock> node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();

  }

  @Override
  public void removeEdge(BasicBlockInContext<IExplodedBasicBlock> src, BasicBlockInContext<IExplodedBasicBlock> dst)
      throws UnsupportedOperationException {
    Assertions.UNREACHABLE();

  }

  @Override
  public void removeIncomingEdges(BasicBlockInContext<IExplodedBasicBlock> node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();

  }

  @Override
  public void removeOutgoingEdges(BasicBlockInContext<IExplodedBasicBlock> node) throws UnsupportedOperationException {
    Assertions.UNREACHABLE();

  }

  @Override
  public int getMaxNumber() {
    return icfg.getMaxNumber();
  }

  @Override
  public BasicBlockInContext<IExplodedBasicBlock> getNode(int number) {
    return icfg.getNode(number);
  }

  @Override
  public int getNumber(BasicBlockInContext<IExplodedBasicBlock> N) {
    return icfg.getNumber(N);
  }

  @Override
  public Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterateNodes(IntSet s) {
    Assertions.UNREACHABLE();
    return null;
  }

  @Override
  public IntSet getPredNodeNumbers(BasicBlockInContext<IExplodedBasicBlock> node) {
    return icfg.getPredNodeNumbers(node);
  }

  @Override
  public IntSet getSuccNodeNumbers(BasicBlockInContext<IExplodedBasicBlock> node) {
    return icfg.getSuccNodeNumbers(node);
  }

  public ControlFlowGraph<SSAInstruction,IExplodedBasicBlock> getCFG(BasicBlockInContext<IExplodedBasicBlock> node) {
    return icfg.getCFG(node);
  }

  public ExplodedInterproceduralCFG getICFG() {
    return icfg;
  }

  @Override
  public String toString() {
    return icfg.toString();
  }

}
