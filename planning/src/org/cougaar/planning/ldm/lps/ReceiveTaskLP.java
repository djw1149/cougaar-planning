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

import org.cougaar.core.blackboard.*;
import org.cougaar.core.agent.*;
import org.cougaar.core.domain.*;
import org.cougaar.core.mts.SerializationUtils;

import org.cougaar.planning.ldm.*;
import org.cougaar.planning.ldm.plan.Task;
import org.cougaar.planning.ldm.plan.NewTask;
import org.cougaar.planning.ldm.plan.Preference;
import org.cougaar.planning.ldm.plan.PlanElement;
import org.cougaar.planning.ldm.plan.TaskImpl;

import org.cougaar.util.log.Logger;
import org.cougaar.util.log.Logging;

import java.util.Collection;

/**
 * Sample LogicProvider for use by ClusterDispatcher to
 * take an incoming Task (excepting Rescind task) and
 * add to the LogPlan w/side-effect of also disseminating to
 * other subscribers.
 * Only adds tasks that haven't been seen before, allowing stability
 * in the face of wire retransmits.
 **/

public class ReceiveTaskLP
implements LogicProvider, MessageLogicProvider
{
  private static Logger logger = Logging.getLogger(ReceiveTaskLP.class);

  private final RootPlan rootplan;
  private final LogPlan logplan;

  public ReceiveTaskLP(
      RootPlan rootplan,
      LogPlan logplan)
  {
    this.rootplan = rootplan;
    this.logplan = logplan;
  }

  public void init() {
  }

  /**
   * Adds Task to LogPlan... Side-effect = other subscribers also
   * updated. If the task is already in the logplan, then there is
   * probably a change in task preferences. If there is no change in
   * task preferences, then it might be the case that the sending
   * agent has undergone a restart and is trying to resynchronize
   * its tasks. We need to activate the NotificationLP to send the
   * estimated allocation result for the plan element of the task. We
   * do this by publishing a change of the plan element (if it
   * exists).
   * Also, the received task may have been deleted. If so, complete
   * the deletion process by actually removing the task from the
   * blackboard.
   **/
  public void execute(Directive dir, Collection changes)
  {
    if (dir instanceof Task) {
      Task tsk = (Task) dir;
      try {
        Task existingTask = logplan.findTask(tsk);
        if (existingTask == null) {
          // only add if it isn't already there.
          if (tsk.getWorkflow() != null) {
            // Has been marked and the mark remains meaning tsk is the
            // same instance as the sending agent has so we clone it
            if (logger.isDebugEnabled()) {
              logger.debug("Cloning task from same node " + tsk.getUID());
            }
            tsk = cloneAliasedTask(tsk);
          }
          rootplan.add(tsk);
        } else if (tsk.isDeleted()) {
          if (existingTask.isDeleted()) {
            rootplan.remove(existingTask); // Complete the removal
          } else {
            // Whoops! We must have restarted and reverted to a
            // pre-deletion state.
          }
        } else if (tsk == existingTask) {
          // This never happens any more
          rootplan.change(existingTask, changes);
        } else {
          Preference[] newPreferences = ((TaskImpl) tsk).getPreferencesAsArray();
          Preference[] existingPreferences = ((TaskImpl) existingTask).getPreferencesAsArray();
          if (java.util.Arrays.equals(newPreferences, existingPreferences)) {
            PlanElement pe = existingTask.getPlanElement();
            if (pe != null) {
              rootplan.change(pe, changes);	// Cause estimated result to be resent
            }
          } else {
            ((NewTask) existingTask).setPreferences(tsk.getPreferences());
            rootplan.change(existingTask, changes);
          }
        }
      } catch (SubscriberException se) {
        logger.error("Could not add Task to LogPlan: " + tsk, se);
      }
    }
  }

  private Task cloneAliasedTask(final Task task) {
    final byte[][] result1 = new byte[1][];
    ClusterContextTable.withMessageContext(task.getSource(), task.getSource(), task.getDestination(), new Runnable() {
        public void run() {
          try {
            result1[0] = SerializationUtils.toByteArray(task);
          } catch (Exception e) {
            logger.error("Failed to clone aliased task");
          }
        }
      });
    if (result1[0] == null) return task;
    final Task[] result2 = new Task[1];
    ClusterContextTable.withMessageContext(task.getDestination(), task.getSource(), task.getDestination(), new Runnable() {
        public void run() {
          try {
            result2[0] = (Task) SerializationUtils.fromByteArray(SerializationUtils.toByteArray(task));
          } catch (Exception e) {
            logger.error("Failed to clone aliased task");
            result2[0] = task;
          }
        }
      });
    return result2[0];
  }
}
