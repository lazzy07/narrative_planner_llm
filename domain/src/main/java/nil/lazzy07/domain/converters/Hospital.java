/*
* File name: Hospital.java
* Project: 
* Author: Lasantha M Senanayake
* Date created: 2026-03-04 02:15:26
// Date modified: 2026-03-04 02:26:02
* ------
*/

package nil.lazzy07.domain.converters;

import java.util.ArrayList;

import edu.uky.cs.nil.sabre.Fluent;
import edu.uky.cs.nil.sabre.comp.CompiledAction;
import edu.uky.cs.nil.sabre.logic.Expression;
import edu.uky.cs.nil.sabre.logic.Parameter;
import edu.uky.cs.nil.sabre.logic.True;
import edu.uky.cs.nil.sabre.logic.Unknown;

public class Hospital extends DomainConverter {

  public Hospital(Expression initial, int goal) {
    super(initial, goal);
    agents.put("Hathaway", "Dr. Hathaway");
    agents.put("Jones", "Jones");
    agents.put("Ross", "Ross");
    agents.put("Young", "Young");
    places.put("Admissions", "Admissions");
    places.put("PatientRoomA", "Patient Room A");
    places.put("PatientRoomB", "Patient Room B");
    places.put("PatientRoomC", "Patient Room C");
    others.put("Healthy", "No symptoms");
    others.put("SymptomA", "Fever");
    others.put("SymptomB", "Rash");
    others.put("TreatmentA", "Antibiotics");
    others.put("TreatmentB", "Steroids");

    this.domainDescription = """
            There are four locations in this story: the hospital admissions room, exam room A, exam room B, and exam room C. There are four locations in this story: the hospital admissions room, exam room A, exam room B, and exam room C. There are two fictional diseases in this story, and only these two diseases exist. The first disease is named Flaze. The symptom of Flaze is a fever. The treatment for Flaze is antibiotics. The second disease is named Jarkis. The symptom of Jarkis is a rash. The treatment for Jarkis is steroids. There are four characters in this story. Hathaway is a doctor. Hathaway wants all hospital patients to be healthy. Jones is a hospital patient. Jones wants to be alive and healthy. Ross is a hospital patient. Ross wants to be alive and healthy. Young is a hospital patient. Young wants to be alive and healthy. There are four kinds of actions characters can take in the story. A doctor can admit a patient who is in the admission room by assigning them to an exam room. Admitting a patient to an exam room does not change the patient’s location. Admitting a patient increases the doctor’s workload by one. A character can walk from one room to another. A doctor can assess a patient who is in their assigned exam room to see what symptoms they are showing. If a doctor’s workload is three or more, the doctor may make a mistake and believe the patient has a different disease than what they actually have. A doctor can treat a patient who is in their assigned exam room for a disease. If a doctor treats a patient with the treatment that matches their symptoms, the patient becomes healthy, otherwise the patient dies. Regardless of the outcome, treating a patient reduces the doctor’s workload by one.
        """;
  }

  @Override
  public String authorGoal() {
    return "that ends with some patient becoming healthy or some patient dying or both.";
  }

  @Override
  public String fluent(Fluent fluent, Expression value) {
    String str = believes(fluent, value);
    ArrayList<String> args = new ArrayList<>();
    for (Parameter arg : fluent.signature.arguments)
      args.add(arg.toString());
    String arg0 = args.get(0);
    switch (fluent.signature.name) {
      case "at":
        str += standardLocation(arg0, value.toString(), str);
        break;
      case "alive":
        if (value.equals(True.TRUE))
          str += arg0 + " is alive";
        else
          str += arg0 + " is not alive";
        break;
      case "treats":
        str += value + " treats " + arg0;
        break;
      case "symptom":
        str += arg0 + " is experiencing " + value;
        break;
      case "assigned":
        if (value.equals(Unknown.UNKNOWN))
          str += arg0 + " has not been assigned";
        else
          str += arg0 + " is assigned to " + value;
        break;
      case "workload":
        str += arg0 + "'s workload is " + value;
        break;
      default:
        str += fluent + " = " + value;
    }
    return clean(str) + ". ";
  }

  @Override
  public String action(CompiledAction action) {
    String name = action.signature.name;
    ArrayList<String> args = new ArrayList<String>();
    for (Parameter arg : action.signature.arguments)
      args.add(arg.toString());
    String arg0 = args.get(0);
    String str = "";
    switch (name) {
      case "admit":
        str += arg0 + " admits " + args.get(1) + " to " + args.get(2);
        break;
      case "walk":
        str += arg0 + " walks from " + args.get(1) + " to " + args.get(2);
        break;
      case "assess":
        str += arg0 + " assesses " + args.get(1) + " and observes " + args.get(2);
        break;
      case "treat":
        str += arg0 + " treats " + args.get(1) + " using " + args.get(2);
        break;
      default:
        throw new RuntimeException(NO_ACTION + action);
    }
    return clean(str) + ".";
  }

  @Override
  public String characterGoals() {
    return "Patients Jones, Ross, and Young each want to be healthy. "
        + "Dr. Hathaway wants all patients to be healthy. ";
  }

  @Override
  public String actionTypes() {
    return "Characters can walk from room to room, and doctors can admit, assess, and treat patients. ";
  }

  @Override
  public String goal() {
    String text = "The story must end with ";
    switch (goal) {
      case 2:
        return text
            + "one patient having recovered from their symptom and another patient having died from an incorrect assessment. ";
      case 1:
        return text
            + "any patient either recovering from their symptom or dying as a result of an incorrect assessment. ";
      default:
        return "";
    }
  }
}
