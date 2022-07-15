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

	private final Map<String, Double> valueFunction = new LinkedHashMap<>();
	private final Map<String, Richtung> policy = new LinkedHashMap<>();

	public final Double GAMMA = 0.9; // Diminish
	public final Double EPSILON= 0.005; // Threshold

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

		Random random = new Random();

		// Initial policy
		for (Koordinate state :
				states) {
			String koordinate = String.valueOf(state.getZeile()) + state.getSpalte();

			ArrayList<Richtung> actions = getPossibleActions(state, size);
			int rnd = random.nextInt(actions.size());

			policy.put(koordinate,  actions.get(rnd));
		}

		// Initial value function
		for (Koordinate state :
				states) {
			String koordinate = String.valueOf(state.getZeile()) + state.getSpalte();

			double rewardFinalState = getReward(see, state);

			valueFunction.put(koordinate, rewardFinalState);
		}

		System.out.println(states.size() + " possible states:\n" + states + "\n");
		System.out.println("Initial policy for each state:\n" + policy + "\n");
		System.out.println("Initial valueFunction with " + valueFunction.size() + " values:\n" + valueFunction + "\n");

		int iteration = 0;
		while (true){
			double change = 0.0;

			for (Koordinate state :
					states) {
				String koordinate = String.valueOf(state.getZeile()) + state.getSpalte();

				if(getReward(see, state) != 0){
					continue;
				}

				double oldValue = valueFunction.get(String.valueOf(state.getZeile()) + state.getSpalte());
				double newValue = 0;

				for (Richtung action :
						getPossibleActions(state, size)) {
					Koordinate stateAfterAction = new Koordinate(state.getZeile() + action.deltaZ(),state.getSpalte() + action.deltaS());

					double value = getReward(see, state) + GAMMA * valueFunction.get(String.valueOf(stateAfterAction.getZeile()) + stateAfterAction.getSpalte());

					if(value > newValue){
						newValue = value;

						policy.put(koordinate, action);
					}
				}

				valueFunction.put(String.valueOf(state.getZeile()) + state.getSpalte(), newValue);
				change = Math.max(change, Math.abs(oldValue - newValue));
			}

			if(change < EPSILON){
				break;
			}
			iteration++;
		}

		System.out.println("Convergence after " + iteration + " iterations.\n");
		System.out.println("Finished valueFunction with " + valueFunction.size() + " values:\n" + valueFunction + "\n");
		printFancyValueFunction(size);
		printFancyPolicy(size);

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
		Richtung bestAction;

		String koordinate = String.valueOf(playerPosition.getZeile()) + playerPosition.getSpalte();

		bestAction = policy.get(koordinate);

		playerPosition = new Koordinate(playerPosition.getZeile() + bestAction.deltaZ(), playerPosition.getSpalte() + bestAction.deltaS());

		return bestAction;
	}

	@Override
	public void versuchZuende(Zustand endzustand) {

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
		ArrayList<Double> values = new ArrayList<>(valueFunction.values());

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

	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
	public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";
	public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";

	private void printFancyPolicy(int size){
		ArrayList<String> koordinaten = new ArrayList<>(policy.keySet());
		ArrayList<Richtung> values = new ArrayList<>(policy.values());

		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int index = i * size + j % size;

				String print;

				Richtung richtung = values.get(index);
				switch (richtung) {
					case HOCH -> print = ANSI_WHITE_BACKGROUND + " U ";
					case LINKS -> print = ANSI_WHITE_BACKGROUND + " < ";
					case RECHTS -> print = ANSI_WHITE_BACKGROUND + " > ";
					case RUNTER -> print = ANSI_WHITE_BACKGROUND + " D ";
					default -> print = " X ";
				}

				if(valueFunction.get(koordinaten.get(index)) == -1){
					print = ANSI_BLUE_BACKGROUND + "   ";
				} else if (valueFunction.get(koordinaten.get(index)) == 1) {
					print = ANSI_GREEN_BACKGROUND + " Z ";
				} else if (valueFunction.get(koordinaten.get(index)) == 0) {
					print = ANSI_WHITE_BACKGROUND + "   ";
				}

				System.out.print(print + ANSI_RESET);
			}
			System.out.println();
		}
	}
}
