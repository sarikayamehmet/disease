/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package chatBot;

import diseasediagnosis.DataModel;
import diseasediagnosis.DiagnosisApp;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.queryparser.classic.ParseException;

import Ontology.RandGenerator;
import bayesianNetwork.Inference;
import bayesianNetwork.Inference.Pair;
import datastructures.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sun.security.util.DisabledAlgorithmConstraints;

/**
 *
 * @author Karol Abramczyk
 */
public class ChatBot implements ActionListener {

	private DiagnosisApp view;
	private DataModel data;
	private AnswerProcessor aProcessor;
	private State state = State.Idle;
	private SymptomsOccurence totalSymptomsOccurence;
	private ArrayList<String> symptomsToAsk;
	private Disease lastDisease;
	private double probability;

	private final double PROBABILITY_TRESHOLD = 0.99; 
	private Inference engine;
	private String lastAskedSymptom;
	private boolean testsDone = false;

	public enum State { AskedGeneralQuestion, AskedSpecificQuestion, Testing, Idle };

	public ChatBot(DiagnosisApp view, DataModel data, Inference engine) {
		this.view = view;
		this.data = data;
		aProcessor = new AnswerProcessor(data);
		this.engine = engine;
	}

	public void invitation() {
		view.logln(">> Hello! Tell me what ails you?");
		state = State.AskedGeneralQuestion;
	}

	public SymptomsOccurence analyzeInput(String text) {
		SymptomsOccurence foundSymptoms = null;
		try {
			foundSymptoms = aProcessor.searchSymptoms(text);
			
			boolean containsNegation = checkNegation(text);
			boolean containsConfirmation = checkConfirmation(text);
			
			if(state.equals(State.AskedSpecificQuestion) && containsNegation){
//				foundSymptoms = new SymptomsOccurence();
				foundSymptoms.remove(lastAskedSymptom);
				foundSymptoms.put(lastAskedSymptom, false);

			}else if(state.equals(State.AskedSpecificQuestion) && containsConfirmation){
				foundSymptoms.remove(lastAskedSymptom);
				foundSymptoms.put(lastAskedSymptom, true);
			}
			return foundSymptoms;

		} catch (IOException ex) {
			Logger.getLogger(DiagnosisApp.class.getName()).log(Level.SEVERE, null, ex);
		} catch (ParseException ex) {
			Logger.getLogger(DiagnosisApp.class.getName()).log(Level.SEVERE, null, ex);
		}
		
		return new SymptomsOccurence(); 
	}

	private boolean checkConfirmation(String text) {
		String[] tags = text.split("[\\s,;]+");
		List<String> listOfTags = Arrays.asList(tags);
		if (listOfTags.contains("yes")){
				return true;
		}
		return false;
	}

	private boolean checkNegation(String text) {
		String[] tags = text.split("[\\s,;]+");
		List<String> listOfTags = Arrays.asList(tags);
		if (listOfTags.contains("no")){
				return true;
		}
		return false;
	}

	private void processConversation(SymptomsOccurence symptomsOccurence) {
		switch(state) {
		case AskedGeneralQuestion :
			processGeneralAnswer(symptomsOccurence);
			break;
		case AskedSpecificQuestion :

			processSpecificAnswer(symptomsOccurence);
			break;
		case Idle :
			processIdleAnswer(symptomsOccurence);
			break;
		}
	}

	private void processGeneralAnswer(SymptomsOccurence symptomsOccurence) {
		totalSymptomsOccurence = symptomsOccurence;
		if(symptomsOccurence.size() > 0 && data.getSymptoms().size() > symptomsOccurence.size()) {
			engine.invokeInference(totalSymptomsOccurence);
			Pair<Disease, Double> pair = engine.findMostLikelyDisease();
			Disease disease = pair.getLeft();  //should be most probable disease found by bayesian network
			lastDisease = disease;
			probability = pair.getRight();
			System.out.println(disease.toString());
			System.out.println("Probability: " + probability);
			symptomsToAsk = disease.getDiffSymptomNames(new ArrayList<>(totalSymptomsOccurence.keySet()));
			System.out.println("Need to ask: " + symptomsToAsk.toString());
			if(symptomsToAsk != null && symptomsToAsk.size() > 0) {
				askForLackingSymptom(disease, true);
			} 
			else{
				if(probability >= PROBABILITY_TRESHOLD || testsDone){
					finish();
				}else{
					orderTests();
				}
			}
		} else{
			askForAnySymptoms();
		}
	}

	private void askForAnySymptoms() {
		view.logln(">> I can hardly find anything, tell me more about symptoms ");
	}

	private void askForLackingSymptom(Disease disease, boolean isFirstCall) {
		
		String word = "";
		if(isFirstCall == false && disease != null && disease.getName().equals(lastDisease.getName())) {
			word = "still ";
		}
		view.logln(">> It " + word + "looks that you have " + disease.getName() + ". But tell me, do you have " + symptomsToAsk.get(0) + "?");
		state = State.AskedSpecificQuestion;
		lastAskedSymptom = symptomsToAsk.get(0);
	}

	private void processSpecificAnswer(SymptomsOccurence symptomsOccurence) {
		if(symptomsOccurence.size() == 0){
			view.logln(">> Please answer in complete sentence.");
			System.out.println("Need to ask: " + symptomsToAsk.toString());
			askForLackingSymptom(lastDisease, false);
		} else {
			symptomsToAsk.removeAll(totalSymptomsOccurence.keySet());
			updateOccurances(symptomsOccurence);

			engine.invokeInference(totalSymptomsOccurence);
			Pair<Disease, Double> pair = engine.findMostLikelyDisease();
			Disease disease = pair.getLeft();  //should be most probable disease found by bayesian network
			lastDisease = disease;
			probability = pair.getRight();
			System.out.println(disease.toString());
			System.out.println("Probability: " + probability);
			symptomsToAsk = disease.getDiffSymptomNames(new ArrayList<>(totalSymptomsOccurence.keySet()));
			System.out.println("Need to ask: " + symptomsToAsk.toString());
			
			if(symptomsToAsk.size() > 0) {
				askForLackingSymptom(disease, false);
			} else {
				if(probability >= PROBABILITY_TRESHOLD || testsDone ){
					finish();
				}else{
					orderTests();
				}
			}
		}
	}

	private void updateOccurances(SymptomsOccurence symptomsOccurence) {
		if(totalSymptomsOccurence!= symptomsOccurence){
			Iterator<Entry<String, Boolean>> iterator = symptomsOccurence.entrySet().iterator();
			while (iterator.hasNext()) {
				Entry<String, Boolean> entry = iterator.next();
				String key = entry.getKey();
				totalSymptomsOccurence.remove(key);			
			}
	
			totalSymptomsOccurence.putAll(symptomsOccurence);
		}
	}

	private void orderTests() {
//		view.logln(">> I am not sure, Lest's make some tests...");
		DiseaseTest test = engine.findMostSuitableTest(Inference.VoITestType.MostProbableElimination, lastDisease);
		if(test == null){
			testsDone = true;
			view.logln(">> There are no tests left to be done...");
			finish();
			return;
		}
		boolean testPositive = engine.getTestResults(test);

		view.logln(">> I am not sure, Lest's make some tests...");
		view.logln(">> " + test.getName()+ "...");
		if(testPositive){
			view.logln(">> Great, test result is positive!");
		}else{
			view.logln(">> Great, test result is negative!");
		}
			
		totalSymptomsOccurence.put(test.getName(),testPositive);
		state=State.AskedSpecificQuestion;
		processSpecificAnswer(totalSymptomsOccurence);
	}

	private void finish() {
		view.logln(">> Now I'm sure - you have " + lastDisease.getName() + "!");	
	}

	private void processIdleAnswer(SymptomsOccurence symptomsOccurence) {
		view.logln(">> As I told you, most probably you have " + lastDisease.getName() + ". Now let me have a brake.");
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String text = view.getTextField().getText();
		view.logln(text);
		//        logger.setForeground(Color.BLUE);
		view.getTextField().selectAll();
		SymptomsOccurence symptoms = analyzeInput(text);
		processConversation(symptoms);        
	}

//	private Disease generateSampleDisease() {
//		Disease disease = new Disease("cold");
//		disease.setDiseaseProbability(0.9);
//		DiseaseSymptom s1 = new DiseaseSymptom("cough");
//		DiseaseSymptom s2 = new DiseaseSymptom("sneezing");
//		DiseaseSymptom s3 = new DiseaseSymptom("vomiting");
//		DiseaseProbabilityBean bean1 = new DiseaseProbabilityBean(disease, s1, 0.6, 0.3);
//		DiseaseProbabilityBean bean2 = new DiseaseProbabilityBean(disease, s2, 0.8, 0.4);
//		DiseaseProbabilityBean bean3 = new DiseaseProbabilityBean(disease, s3, 0.2, 0.3);
//		disease.getSymptoms().put(s1, bean1);
//		disease.getSymptoms().put(s2, bean2);    
//		disease.getSymptoms().put(s3, bean3);
//		return disease;
//	}



}
