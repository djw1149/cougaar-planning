/*
 * <copyright>
 *  Copyright 1997-2003 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */


package org.cougaar.planning.ldm.lps;

import java.util.ArrayList;
import java.util.Collection;

import org.cougaar.core.blackboard.BlackboardServesDomain;
import org.cougaar.core.blackboard.Directive;
import org.cougaar.core.domain.DelayedLPAction;
import org.cougaar.core.domain.LogicProvider;
import org.cougaar.core.domain.MessageLogicProvider;
import org.cougaar.core.domain.RootPlan;
import org.cougaar.core.util.UID;
import org.cougaar.planning.ldm.LogPlan;
import org.cougaar.planning.ldm.PlanningFactory;
import org.cougaar.planning.ldm.plan.Aggregation;
import org.cougaar.planning.ldm.plan.Allocation;
import org.cougaar.planning.ldm.plan.AllocationResult;
import org.cougaar.planning.ldm.plan.AllocationforCollections;
import org.cougaar.planning.ldm.plan.AssetTransfer;
import org.cougaar.planning.ldm.plan.Expansion;
import org.cougaar.planning.ldm.plan.Notification;
import org.cougaar.planning.ldm.plan.PEforCollections;
import org.cougaar.planning.ldm.plan.PlanElement;
import org.cougaar.planning.ldm.plan.TaskRescind;
import org.cougaar.planning.ldm.plan.WorkflowImpl;
import org.cougaar.util.log.Logger;
import org.cougaar.util.log.Logging;

/**
 * Sample LogicProvider for use by ClusterDispatcher to
 * take an incoming Notification Directive and
 * perform Modification to the LOGPLAN
 **/

public class ReceiveNotificationLP
implements LogicProvider, MessageLogicProvider
{
  private static final Logger logger = Logging.getLogger(ReceiveNotificationLP.class);

  private final RootPlan rootplan;
  private final LogPlan logplan;
  private final PlanningFactory ldmf;

  public ReceiveNotificationLP(
      RootPlan rootplan,
      LogPlan logplan,
      PlanningFactory ldmf) {
    this.rootplan = rootplan;
    this.logplan = logplan;
    this.ldmf = ldmf;
  }

  public void init() {
  }

  /**
   *  perform updates -- per Notification ALGORITHM --
   *
   **/
  public void execute(Directive dir, Collection changes) {
    if (dir instanceof Notification) {
      processNotification((Notification) dir, changes);
    }
  }

  private void processNotification(Notification not, Collection changes) {
    UID tuid = not.getTaskUID();
    UID childuid = not.getChildTaskUID();
    PlanElement pe = logplan.findPlanElement(tuid);
    boolean needToRescind = (pe == null);

    // verify that the pe matches the task
    if (!needToRescind &&  (pe instanceof AllocationforCollections)) {
      UID remoteTUID = ((AllocationforCollections)pe).getAllocationTaskUID();
      if (remoteTUID == null) {
        needToRescind = true;
      } else {
        if (!(remoteTUID.equals(childuid))) {
          // this was likely due to replacing the Allocation
          if (logger.isInfoEnabled()) {
            logger.info("Got a Notification for the wrong allocation:"+
                        "\n\tTask="+tuid+
                        "  ("+pe.getTask().getUID()+")"+
                        "\n\tFrom="+childuid+
                        "  ("+remoteTUID+")"+
                        "\n\tResult="+not.getAllocationResult()+"\n"+
                        "\n\tPE="+pe);
          }
          needToRescind = true; // Insure that the old child task is gone.
          return;
        }
      }
    }

    if (needToRescind) {
      TaskRescind trm = ldmf.newTaskRescind(childuid, not.getSource());
      rootplan.sendDirective(trm, changes);
    } else {
      AllocationResult ar = not.getAllocationResult();
      propagateNotification(
          rootplan, logplan, pe, tuid, ar, childuid, changes);
    }
  }

  // default protection so that NotificationLP can call this method
  static final void propagateNotification(
      RootPlan rootplan,
      LogPlan logplan,
      UID tuid, AllocationResult result,
      UID childuid, Collection changes) {
    PlanElement pe = logplan.findPlanElement(tuid);
    if (pe != null) {
      propagateNotification(
          rootplan, logplan, pe, tuid, result, childuid, changes);
    } else {
      if (logger.isDebugEnabled()) {
	logger.debug("Received notification about unknown task: "+tuid);
      }
    }
  }

  // default protection so that NotificationLP can call this method
  static final void propagateNotification(
      RootPlan rootplan,
      LogPlan logplan,
      PlanElement pe,
      UID tuid, AllocationResult result,
      UID childuid, Collection changes) {

    // compare getReceivedResult .isEquals with this new one -- reconciliation after restart
    // is going to resend all the ARs, and we should avoid propagating the changes
    if (result == null || result.isEqual(pe.getReceivedResult())) {
      if (logger.isInfoEnabled())
	logger.info("Not propagating unchanged ReceivedResult for PE " + pe);
      return;
    }

    if ((pe instanceof Allocation) ||
        (pe instanceof AssetTransfer) ||
        (pe instanceof Aggregation)) {
      ((PEforCollections) pe).setReceivedResult(result);
      if (logger.isDebugEnabled())
	logger.debug("pubChanging local pe: " + pe);
      rootplan.change(pe, changes);
    } else if (pe instanceof Expansion) {
      rootplan.delayLPAction(
          new DelayedAggregateResults((Expansion)pe, childuid));

      /*
      Workflow wf = ((Expansion)pe).getWorkflow();
      AllocationResult ar = wf.aggregateAllocationResults();
      if (ar != null) {
	// get the TaskScoreTable used in the aggregation
	TaskScoreTable aggTST = ((WorkflowImpl)wf).getCurrentTST();
	// get the UID of the child task that caused this aggregation
	((ExpansionImpl)pe).setSubTaskResults(aggTST,childuid);
        ((PEforCollections) pe).setReceivedResult(ar);
	rootplan.change(pe, changes);
      } // if we can't successfully aggregate the results - don't send a notification
      */
    /*
    } else if (pe instanceof Disposition) {
      // drop the notification on the floor - cannot possibly be valid
    }
    */
    } else {
      if (logger.isInfoEnabled()) {
        logger.info("Got a Notification for an inappropriate PE:\n"+
                    "\tTask="+tuid+"\n"+
                    "\tFrom="+childuid+"\n"+
                    "\tResult="+result+"\n"+
                    "\tPE="+pe);
      }
    }
  }

  /** delay the results aggregation of an expansion until the end in case
   * we have lots of them to do.
   **/
  private final static class DelayedAggregateResults
    implements DelayedLPAction
  {
    private final Expansion pe;
    private final ArrayList ids = new ArrayList(1);
    DelayedAggregateResults(Expansion pe, UID id) {
      this.pe = pe;
      ids.add(id);
    }

    public void execute(BlackboardServesDomain bb) {
      WorkflowImpl wf = (WorkflowImpl) pe.getWorkflow();

      // compute the new result from the subtask results.
      try {
        AllocationResult ar = wf.aggregateAllocationResults(ids);
        if (ar != null) {         // if the aggregation is defined:
          // set the result on the
          ((PEforCollections) pe).setReceivedResult(ar);

          // publish the change to the blackboard.
          bb.change(pe, null); // drop the change details.
          //bb.change(pe, changes);
          //Logging.printDot("=");
        }
      } catch (RuntimeException re) {
        logger.error("Caught exception while processing DelayedAggregateResults for "+pe, re);
      }
    }

    /** hashcode is the hashcode of the expansion **/
    public int hashCode() {
      return pe.hashCode();
    }

    /** these guys are equal iff the they have the same PE **/
    public boolean equals(Object e) {
      return (e instanceof DelayedAggregateResults &&
              ((DelayedAggregateResults)e).pe == pe);
    }

    /** merge another one into this one **/
    public void merge(DelayedLPAction e) {
      // don't bother to check, since we will only be here if this.equals(e).
      DelayedAggregateResults other = (DelayedAggregateResults) e;
      ids.addAll(other.ids);
    }
  }
}
