/*
 * <copyright>
 *  
 *  Copyright 2000-2004 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 * 
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 * </copyright>
 */

package org.cougaar.planning.examples.servlet;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.blackboard.BlackboardClient;
import org.cougaar.core.component.*;
import org.cougaar.core.service.*;
import org.cougaar.core.servlet.BaseServletComponent;

import org.cougaar.planning.ldm.PlanningFactory;
import org.cougaar.planning.ldm.plan.*;

public class AddTaskServletComponent 
extends BaseServletComponent 
implements BlackboardClient
{

  private MessageAddress agentId;
  private BlackboardService blackboard;
  private DomainService ds;
  private PlanningFactory ldmf;

  public void load() {
    AgentIdentificationService ais = (AgentIdentificationService)
      serviceBroker.getService(
          this, AgentIdentificationService.class, null);
    if (ais != null) {
      this.agentId = ais.getMessageAddress();
      serviceBroker.releaseService(
          this, AgentIdentificationService.class, ais);
    }

    super.load();
  }

  protected String getPath() {
    return "/addtask";
  }

  //
  // These "setXService(XService x) {..}" methods
  // are equivalent to the SimpleServletComponent's
  // "public void load() { .. serviceBroker.getService(..); .. }"
  // calls, EXCEPT that:
  //   1) these methods are only called at load-time.
  //   2) if one of these services is not available then this 
  //      Component will NOT be loaded.  In contrast, the 
  //      "load()" pattern allows the Component to (optionally) 
  //      continue loading even if any "getService(..)" returns null.
  //   3) these "setXService(..)" will request the service with
  //      "this" as the requestor.  The more generic "getService(..)"
  //      API allows the Component to pass a different class
  //      (e.g. an inner class to handle callbacks).
  //

  public void setBlackboardService(BlackboardService blackboard) {
    this.blackboard = blackboard;
  }

  public void setDomainService(DomainService ds) {
    this.ds = ds;
    this.ldmf = (PlanningFactory) ds.getFactory("planning");
  }

  protected Servlet createServlet() {
    return new MyServlet();
  }

  public void unload() {
    super.unload();
    // FIXME release the rest!
  }

  private class MyServlet extends HttpServlet {
    public void doGet(
        HttpServletRequest req,
        HttpServletResponse res) throws IOException {
      PrintWriter out = res.getWriter();
      out.println("<html><body bgcolor=\"white\">");

      // add a new task to the blackboard:
      NewTask nt = ldmf.newTask();
      nt.setSource(agentId);
      nt.setVerb(Verb.get("fromAddTaskServlet"));
      try {
        blackboard.openTransaction();
        blackboard.publishAdd(nt);
      } finally {
        blackboard.closeTransactionDontReset();
      }

      out.println(
          "AddTaskServletComponent "+
          "publish-Added task (<tt>"+
          nt.getUID()+
          "</tt>) with verb \"<tt>"+
          nt.getVerb()+
          "</tt>\" to agent <b>"+
          agentId+
          "</b>'s Blackboard.");
      out.println("</body></html>");
    }
  }

  //
  // These are oddities of implementing BlackboardClient:
  //
  // Note: A Component must implement BlackboardClient in order 
  // to obtain BlackboardService.
  //

  // odd BlackboardClient method:
  public String getBlackboardClientName() {
    return toString();
  }

  // odd BlackboardClient method:
  public long currentTimeMillis() {
    throw new UnsupportedOperationException(
        this+" asked for the current time???");
  }

  // unused BlackboardClient method:
  public boolean triggerEvent(Object event) {
    // if we had Subscriptions we'd need to implement this.
    //
    // see "ComponentPlugin" for details.
    throw new UnsupportedOperationException(
        this+" only supports Blackboard queries, but received "+
        "a \"trigger\" event: "+event);
  }
}
