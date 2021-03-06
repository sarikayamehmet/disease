package bayesianNetwork;

/**
 * 
 */
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import smile.Network;
import smile.SMILEException;
import chatBot.SymptomsOccurence;
import datastructures.Disease;
import datastructures.DiseaseClue;
import datastructures.DiseaseProbabilityBean;
import datastructures.DiseaseSymptom;
import datastructures.DiseaseTest;

/**
 * @author maria
 * 
 */
public class Inference {

	/**
	 * @param args
	 */

	private HashSet<Pair<String, String>> observed; // a list of clues observed for
	private HashSet<Pair<Integer, String>> observedNodes;								// particular case
	private final String YES = NetworkStructure.YES;
	private final String NO = NetworkStructure.NO;
	private Network network;
	private Map<String, Disease> diseases; 
	private Map<DiseaseTest, Boolean> conductedTests;
//	private final String networkFileName = "tutorial_a.xdsl";
	private String mostProbableDisease;
	public static enum VoITestType {MostProbableElimination, Simple, Exhaustive};
	private double maxProbability;
	private NetworkStructure networkStructure;


//	public static void main(String[] args) {
//
//		// for test/presentation purposes
//		new NetworkStructure();
//		InfereceWithBayesianNetwork();
//	}
//
//	public Inference() {
//		loadNetworkFromFile();
//		observed = new ArrayList<Pair<String, String>>();
//		mostProbableDisease = null;
//
//	}
//
//	private void loadNetworkFromFile() {
//		try {
//			net = new Network();
//			net.readFile(networkFileName);
//		} catch (SMILEException e) {
//			System.out.println(e.getMessage());
//		}
//	}
	
	public Inference(Map <String, Disease> diseases ,  Map <String, DiseaseSymptom> symptoms) {
		super();
		networkStructure = new NetworkStructure();
//		net = networkStructure.CreateNetwork(diseases ,  symptoms);
		network = networkStructure.CreateNetwork(diseases);
		this.diseases = diseases; 

		observed = new HashSet<Pair<String, String>>();
		observedNodes = new HashSet<Pair<Integer, String>>();
		mostProbableDisease = null;
		conductedTests = new HashMap<DiseaseTest, Boolean>();
		//listAvailableTests();
	}
	
	public void invokeInference(SymptomsOccurence symptoms){
		Iterator<Entry<String, Boolean>>  it = symptoms.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, Boolean>  pair = it.next();
			addEvidence(pair.getKey(), pair.getValue());
		}
		
		//TODO run this in new thread??
		runInference();
	}

	public void addEvidence(String clueName, boolean isPositive // TRUE if clue occurs
																// FALSE if doesn't
	) {
		this.observed.add(new Pair<String, String>(clueName, isPositive ? YES
				: NO));
		ArrayList<Integer> nodeIDs = networkStructure.resolveClueToNode(clueName);
		if (null != nodeIDs) {
			if (!nodeIDs.isEmpty()) {
				for (Integer position : nodeIDs) {
					this.observedNodes.add(new Pair<Integer, String>(position,
							isPositive ? YES : NO));
				}
			} else {
				System.out
						.println("addEvidence error: clue is not added to bayesian network");
			}
		} else {
			System.out.println("addEvidence error: no clue on the list: " + clueName);
		}

	}


	public Pair<Disease, Double> findMostLikelyDisease() {
		return new Pair<Disease, Double> (diseases.get(mostProbableDisease), new Double(maxProbability));
	}

	public DiseaseTest findMostSuitableTest(VoITestType type, Disease disease) {
		
		switch (type) {
		case MostProbableElimination :
			return mostProbableEliminationTest(disease);
		case Simple :
			return simpleTest(disease);
		default :
		break;
		}
		
		return null;
	}
	

	private DiseaseTest mostProbableEliminationTest(Disease disease) {
		DiseaseTest bestTest = null;
		double testVoI = 0.0f;

		Map<DiseaseTest, DiseaseProbabilityBean> tests = disease.getTests();
		Iterator<Entry<DiseaseTest, DiseaseProbabilityBean>> entries = tests.entrySet().iterator();
		
		while (entries.hasNext()) {
			Entry<DiseaseTest, DiseaseProbabilityBean> entry = entries.next();
			double VoI = entry.getValue().getpSgivenD()
					- entry.getValue().getpSgivenNotD();
			DiseaseTest test =  (DiseaseTest) entry.getKey();
			if (VoI > testVoI && !conductedTests.containsKey(test) ) {
				testVoI = VoI;
				bestTest =test;
			}
		}
		
//		if(!conductedTests.containsKey(bestTest)){
//			conductedTests.put(bestTest, Boolean.TRUE);
//			return bestTest;
//		}
		conductedTests.put(bestTest, true);
		return bestTest;
	}
	
//	private void listAvailableTests()
//	{
//		for (Disease value : diseases.values()) {
//			for (DiseaseClue test : value.getTests().keySet())
//			{
//				wasTestConducted.put((DiseaseTest) test, false);
//			}
//		}
//	}
		
	private DiseaseTest simpleTest(Disease disease)
	{
		Iterator<Entry<DiseaseTest, DiseaseProbabilityBean>> iterator = disease.getTests().entrySet().iterator();
		while(iterator.hasNext()){
			Entry<DiseaseTest, DiseaseProbabilityBean> entry = iterator.next();
			DiseaseTest test = entry.getKey();
			
			if(!conductedTests.containsKey(test)){
				conductedTests.put(test, Boolean.TRUE);
				return test;		

			}
		}
		return null;
	}

	private void runInference() {
		network.clearAllEvidence();
		maxProbability = 0;
		try {
			for (Pair<Integer, String> observation : observedNodes) {

				network.setEvidence(observation.getLeft(),
						observation.getRight());
			}
			network.updateBeliefs();
			

			Iterator<Entry<String, Disease>> iterator = diseases.entrySet().iterator();

//			for (int i = 0; i < diseases.size(); ++i) {
			while(iterator.hasNext()){
				Disease d = iterator.next().getValue();
				Integer diseaseNodeNumber =  networkStructure.resolveDiseaseToNode(d.getName());
				if(diseaseNodeNumber == null)
					continue;
	//			network.getNode(diseaseNodeNumber);
	//			network.getNode
				double probab = network.getNodeValue(diseaseNodeNumber)[0];
				if (probab > maxProbability) {
					maxProbability = probab;
					this.mostProbableDisease = d.getName();
				}
			}

		} catch (SMILEException e) {
			System.out.println(e.getMessage());
		}
	}

	private static void InfereceWithBayesianNetwork() { // TEST METHOD
		try {
			Network net_test = new Network();
			net_test.readFile("tutorial_a.xdsl");

			double[] aValues;

			net_test.setEvidence("Fever", "Yes");

			net_test.updateBeliefs();

			net_test.getNode("Flu");
			aValues = net_test.getNodeValue("Flu");

			System.out.println("Fever yes");
			System.out.println("P(Influenza=T|evidence)= " + aValues[0]);
			System.out.println("P(Influenza=F|evidence)= " + aValues[1]);

			net_test.clearAllEvidence();

			net_test.setEvidence("Fever", "Yes");
			// net.setEvidence("BackPain", "Yes");
			// Updating the network:
			net_test.updateBeliefs();

			// Getting the handle of the node "Success":
			net_test.getNode("Clap");
			aValues = net_test.getNodeValue("Clap");

			System.out.println("Fever yes");
			System.out.println("P(Clap=T|evidence)= " + aValues[0]);
			System.out.println("P(Clap=F|evidence)= " + aValues[1]);

			net_test.clearAllEvidence();

			net_test.setEvidence("Fever", "No");
			// net.setEvidence("BackPain", "Yes");
			// Updating the network:
			net_test.updateBeliefs();

			// Getting the handle of the node "Success":
			net_test.getNode("Flu");
			aValues = net_test.getNodeValue("Flu");

			System.out.println("Fever no");
			System.out.println("P(Influenza=T|evidence)= " + aValues[0]);
			System.out.println("P(Influenza=F|evidence)= " + aValues[1]);

			net_test.clearAllEvidence();

			net_test.setEvidence("Fever", "No");
			// net.setEvidence("BackPain", "Yes");
			// Updating the network:
			net_test.updateBeliefs();

			// Getting the handle of the node "Success":
			net_test.getNode("Clap");
			aValues = net_test.getNodeValue("Clap");

			System.out.println("Fever no");
			System.out.println("P(Clap=T|evidence)= " + aValues[0]);
			System.out.println("P(Clap=F|evidence)= " + aValues[1]);

			net_test.clearAllEvidence();

			/*
			 * net.setEvidence("BackPain", "No"); //net.setEvidence("BackPain",
			 * "Yes"); // Updating the network: net.updateBeliefs();
			 * 
			 * net.getNode("Flu"); aValues = net.getNodeValue("Flu");
			 * System.out.println("BackPain no");
			 * System.out.println("P(Influenza=T|evidence)= " + aValues[0]);
			 * System.out.println("P(Influenza=F|evidence)= " + aValues[1]);
			 * 
			 * net.clearAllEvidence();
			 * 
			 * net.setEvidence("Sneezing", "Yes"); //net.setEvidence("BackPain",
			 * "Yes"); // Updating the network: net.updateBeliefs();
			 * 
			 * net.getNode("Flu"); aValues = net.getNodeValue("Flu");
			 * System.out.println("Sneezing yes");
			 * System.out.println("P(Influenza=T|evidence)= " + aValues[0]);
			 * System.out.println("P(Influenza=F|evidence)= " + aValues[1]);
			 * 
			 * net.clearAllEvidence();
			 * 
			 * net.setEvidence("Fever", "Yes"); net.setEvidence("BackPain",
			 * "Yes"); net.setEvidence("Sneezing", "Yes"); // Updating the
			 * network: net.updateBeliefs();
			 * 
			 * net.getNode("Flu"); aValues = net.getNodeValue("Flu");
			 * 
			 * System.out.println("fever yes back pain no sneezing Yes");
			 * System.out.println("P(Influenza=T|evidence)= " + aValues[0]);
			 * System.out.println("P(Influenza=F|evidence)= " + aValues[1]);
			 */

		} catch (SMILEException e) {
			System.out.println(e.getMessage());
		}
	}

	public class Pair<L, R> {

		private final L left;
		private final R right;

		public Pair(L left, R right) {
			this.left = left;
			this.right = right;
		}

		public L getLeft() {
			return left;
		}

		public R getRight() {
			return right;
		}
	}

	public boolean getTestResults(DiseaseTest test) {
		double totalP = 0;
		Entry<Disease, DiseaseProbabilityBean> entry = test.getProbabilities().entrySet().iterator().next();
		Disease d = entry.getKey();
		DiseaseProbabilityBean p = entry.getValue();
//		totalP = d.getDiseaseProbability() * p.getpSgivenD() + (1-d.getDiseaseProbability())*p.getpSgivenNotD();	
		totalP = maxProbability * p.getpSgivenD() + (1-maxProbability)*p.getpSgivenNotD();
		double rand = Math.random();
		if(rand > totalP)
			return false;
		
		return true;
		
	}
}
