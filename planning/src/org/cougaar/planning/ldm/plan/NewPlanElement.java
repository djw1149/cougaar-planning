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

package org.cougaar.planning.ldm.plan;


/** NewPlanElement Interface
 * provides setter methods to build a PlanElement object
 **/

public interface NewPlanElement extends PlanElement {
  /** 
   * @param p - Set the Plan associated with the PlanElement.
   **/
  void setPlan(Plan p);

  /** This sets the Task of the PlanElement. 
   * @param t - The Task that the PlanElement is referencing.
   **/
  void setTask(Task t);
  /**
   * Sets the Task of the PlanElement. This differs from setTask
   * in that it is expected that the PlanElement is already attached
   * to a Task so the Task and PlanElement are rewired accordingly.
   * @param t - The new Task that the PlanElement is referencing.
   **/
  void resetTask(Task t);
}
