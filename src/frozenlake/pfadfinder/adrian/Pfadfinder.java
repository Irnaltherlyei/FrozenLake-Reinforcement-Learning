package frozenlake.pfadfinder.adrian;

import frozenlake.Koordinate;
import frozenlake.Richtung;
import frozenlake.See;
import frozenlake.Zustand;

import java.text.DecimalFormat;
import java.util.*;

public class Pfadfinder implements frozenlake.pfadfinder.IPfadfinder{

	@Override
	public String meinName() {
		return "adrian";
	}

	// Doesn't work because no equal method exists for class Koordinate
	private final Map<Koordinate, Double> valueFunction = new LinkedHashMap<>();
	private final Map<String, Double> valueFunctionString = new LinkedHashMap<>();
	private final Map<String, Richtung> policy = new LinkedHashMap<>();


	private final Double GAMMA = 0.9; // Diminish
	private final Double EPSILON= 0.005; // Threshold

	@Override
	public boolean lerneSee(See see, boolean stateValue, boolean neuronalesNetz, boolean onPolicy) {
		int size = see.getGroesse();

		// Define all possible states
		ArrayList<Koordinate> states = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				states.add(new Koordinate(i, j));
			}
		}

		// Initial actions at each state
		Map<String, ArrayList<Richtung>> possibleActions = new LinkedHashMap<>();
		for (Koordinate state :
				states) {
			String koordinate = String.valueOf(state.getZeile()) + state.getSpalte();

			possibleActions.put(koordinate, getPossibleActions(state, size));
		}

		Random random = new Random();

		// Initial policy
		for (Koordinate state :
				states) {
			String koordinate = String.valueOf(state.getZeile()) + state.getSpalte();

			ArrayList<Richtung> actions = possibleActions.get(koordinate);
			int rnd = random.nextInt(actions.size());

			policy.put(koordinate,  actions.get(rnd));
		}

		// Initial value function
		for (Koordinate state :
				states) {
			String koordinate = String.valueOf(state.getZeile()) + state.getSpalte();

			double rewardFinalState = getReward(see, state);

			//valueFunction.put(state, 0.0);
			valueFunctionString.put(koordinate, rewardFinalState);
		}

		System.out.println(states.size() + " possible states:\n" + states);
		System.out.println("Possible actions for each state:\n" + possibleActions);
		System.out.println("Initial policy for each state:\n" + policy);
		//System.out.println("Initial valueFunction:\n" + valueFunction);
		System.out.println("Initial valueFunctionString with " + valueFunctionString.size() + " values:\n" + valueFunctionString);
		System.out.println();

		int iteration = 0;
		while (true){
			double change = 0.0;

			for (Koordinate state :
					states) {
				String koordinate = String.valueOf(state.getZeile()) + state.getSpalte();

				if(getReward(see, state) != 0){
					continue;
				}

				//double oldValue = valueFunction.get(state);
				double oldValue = valueFunctionString.get(String.valueOf(state.getZeile()) + state.getSpalte());
				double newValue = 0;

				for (Richtung action :
						possibleActions.get(koordinate)) {
					Koordinate stateAfterAction = new Koordinate(state.getZeile() + action.deltaZ(),state.getSpalte() + action.deltaS());

					//double value = getReward(see, state) + GAMMA * valueFunction.get(stateAfterAction);
					double value = getReward(see, state) + GAMMA * valueFunctionString.get(String.valueOf(stateAfterAction.getZeile()) + stateAfterAction.getSpalte());

					if(value > newValue){
						newValue = value;

						policy.put(koordinate, action);
					}
				}

				//valueFunction.put(state, newValue);
				valueFunctionString.put(String.valueOf(state.getZeile()) + state.getSpalte(), newValue);
				change = Math.max(change, Math.abs(oldValue - newValue));
			}

			if(change < EPSILON){
				break;
			}
			iteration++;
		}

		//System.out.println(valueFunction);
		System.out.println("Convergence after " + iteration + " iterations.");
		System.out.println("Finished valueFunctionString with " + valueFunctionString.size() + " values:\n" + valueFunctionString);
		System.out.println();
		printFancyValueFunction(size);
		System.out.println();
		printFancyPolicy(size);
		System.out.println();

		return true;
	}

	Koordinate playerPosition;
	int size;

	@Override
	public boolean starteUeberquerung(See see, boolean stateValue, boolean neuronalesNetz, boolean onPolicy) {
		playerPosition = see.spielerPosition();
		size = see.getGroesse();
		return false;
	}

	@Override
	public Richtung naechsterSchritt(Zustand ausgangszustand) {
//		ArrayList<Richtung> possibleActions = getPossibleActions(playerPosition, size);
//
		Richtung bestAction =  null;
//		double highestValue = Double.NEGATIVE_INFINITY;
//
//		for (Richtung action :
//				possibleActions) {
//			Koordinate positionAfterAction = new Koordinate(playerPosition.getZeile() + action.deltaZ(), playerPosition.getSpalte() + action.deltaS());
//
//			String koordinateToString = String.valueOf(positionAfterAction.getZeile()) + positionAfterAction.getSpalte();
//
//			if(valueFunctionString.containsKey(koordinateToString)){
//				double value = valueFunctionString.get(koordinateToString);
//
//				if(value > highestValue){
//					bestAction = action;
//				}
//			}
//		}

		String koordinate = String.valueOf(playerPosition.getZeile()) + playerPosition.getSpalte();

		bestAction = policy.get(koordinate);

		playerPosition = new Koordinate(playerPosition.getZeile() + bestAction.deltaZ(), playerPosition.getSpalte() + bestAction.deltaS());

		return bestAction;
	}

	@Override
	public void versuchZuende(Zustand endzustand) {
		//TODO Hier sind Sie gefragt
	}

	private boolean inBounds(Koordinate position, Richtung action, int size){
		Koordinate positionAfterAction = new Koordinate(position.getZeile() + action.deltaZ(), position.getSpalte() + action.deltaS());

		if(positionAfterAction.getZeile() >= size){
			return false;
		}

		if(positionAfterAction.getSpalte() >= size){
			return false;
		}

		if(positionAfterAction.getZeile() < 0){
			return false;
		}

		return positionAfterAction.getSpalte() >= 0;
	}

	private ArrayList<Richtung> getPossibleActions(Koordinate position, int size){
		ArrayList<Richtung> possibleActions = new ArrayList<>();

		for (Richtung action:
			 Richtung.values()) {
			if(inBounds(position, action, size)){
				possibleActions.add(action);
			}
		}

		return possibleActions;
	}

	private double getReward(See see, Koordinate state){
		if(see.zustandAn(state) == Zustand.Wasser || see.zustandAn(state) == Zustand.UWasser){
			return -1.0;
		}

		if(see.zustandAn(state) == Zustand.Ziel){
			return 1.0;
		}

		if(see.zustandAn(state) == Zustand.Eis || see.zustandAn(state) == Zustand.UEis || see.zustandAn(state) == Zustand.Start){
			return 0.0;
		}

		return Double.NEGATIVE_INFINITY;
	}

	private void printFancyValueFunction(int size){
		ArrayList<Double> values = new ArrayList<>(valueFunctionString.values());

		DecimalFormat num = new DecimalFormat("0.00");
		num.setPositivePrefix("+");

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				double value = values.get(i * size + j % size);

				System.out.print(num.format(value) + " | ");
			}
			System.out.println();
		}
	}

	private void printFancyPolicy(int size){
		ArrayList<Richtung> values = new ArrayList<>(policy.values());

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				Richtung richtung = values.get(i * size + j % size);

				String print = "";

				if(richtung == null){
					print = "   ";
				} else {
					switch (richtung){
						case HOCH -> print = " U ";
						case LINKS -> print = " < ";
						case RECHTS -> print = " > ";
						case RUNTER -> print = " D ";
						default -> print = " X ";
					}
				}

				System.out.print(print + "|");
			}
			System.out.println();
		}
	}
}
