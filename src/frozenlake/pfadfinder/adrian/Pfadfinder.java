package frozenlake.pfadfinder.adrian;

import frozenlake.Koordinate;
import frozenlake.Richtung;
import frozenlake.See;
import frozenlake.Zustand;

import java.text.DecimalFormat;
import java.util.*;

public class Pfadfinder implements frozenlake.pfadfinder.IPfadfinder{

	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
	public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";
	public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";

	private Koordinate playerPosition;
	private boolean stateValue = false;

	private final Map<String, Double> valueFunction = new LinkedHashMap<>();
	private final Map<String, Richtung> policy = new LinkedHashMap<>();

	private final Map<String, Map<Richtung, Double>> q_table = new LinkedHashMap<>();

	@Override
	public String meinName() {
		return "adrian";
	}

	@Override
	public boolean lerneSee(See see, boolean stateValue, boolean neuronalesNetz, boolean onPolicy) {
		if(stateValue){
			this.stateValue = true;
			stateValue(see);
		} else {
			Q_Learning(see, 10000);
		}

		return true;
	}

	@Override
	public boolean starteUeberquerung(See see, boolean stateValue, boolean neuronalesNetz, boolean onPolicy) {
		playerPosition = see.spielerPosition();
		return true;
	}

	@Override
	public Richtung naechsterSchritt(Zustand ausgangszustand) {
		Richtung bestAction;

		String koordinate = koordinateToString(playerPosition);

		if(stateValue){
			bestAction = policy.get(koordinate);
		} else {
			Optional<Map.Entry<Richtung, Double>> maxEntry = q_table.get(koordinate).entrySet()
					.stream()
					.max(Map.Entry.comparingByValue());
			bestAction = maxEntry.get().getKey();
		}

		playerPosition = new Koordinate(playerPosition.getZeile() + bestAction.deltaZ(), playerPosition.getSpalte() + bestAction.deltaS());

		return bestAction;
	}

	@Override
	public void versuchZuende(Zustand endzustand) {
		// I don't know what to put here
	}

	private void Q_Learning(See see, int numEpisodes){
		int size = see.getGroesse();
		System.out.println(size);

		// Initialize Q-Table as map with coordinates as string representing the state
		// and the value as another map having all possible actions as key and the corresponding
		// Q-Table value with the state and the action as value.
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				Koordinate state = new Koordinate(i,j);
				String stateAsString = koordinateToString(state);

				Map<Richtung, Double> actions = new LinkedHashMap<>();

				for (Richtung action : Richtung.values()){
					actions.put(action, 0.0);
				}

				q_table.put(stateAsString, actions);
			}
		}

		int maxStepsInEpisode = size * size;

		double learningRate = 0.1;
		double discountRate = 0.99;

		double explorationRate = 1;
		double maxExplorationRate = 1;
		double minExplorationRate = 0.01;
		double explorationDecayRate = 0.001;

		double[] reward_per_episode = new double[numEpisodes];

		for (int episode = 0; episode < numEpisodes; episode++) {
			see.neustartSpielfigur();

			boolean done = false;
			double reward_current_episode = 0;

			for (int step = 0; step < maxStepsInEpisode; step++) {
				Koordinate position = new Koordinate(see.spielerPosition().getZeile(), see.spielerPosition().getSpalte());

				Richtung action = null;

				double exploration_rate_threshold = Math.random();
				if(exploration_rate_threshold > explorationRate){
					// Exploit
//					Optional<Map.Entry<Richtung, Double>> maxEntry = q_table.get(koordinateToString(position)).entrySet()
//							.stream()
//							.max(Map.Entry.comparingByValue());
//					action = maxEntry.get().getKey();
					double maxValue = Double.NEGATIVE_INFINITY;
					for (Map.Entry<Richtung, Double> rowState : q_table.get(koordinateToString(position)).entrySet()){
						if(rowState.getValue() > maxValue && getPossibleActions(position, size).contains(rowState.getKey())){
							maxValue = rowState.getValue();
							action = rowState.getKey();
						}
					}
				} else {
					// Explore
					ArrayList<Richtung> possibleActions = getPossibleActions(position, size);
					action = possibleActions.get(new Random().nextInt(possibleActions.size()));
				}

				// Take action
				double reward;
				//try {
					see.geheNach(action);

					Koordinate newPosition = new Koordinate(see.spielerPosition().getZeile(), see.spielerPosition().getSpalte());
					String newPositionAsString = koordinateToString(newPosition);

					//System.out.println("From " + position + " to " + newPosition + " with " + action);

					reward = getReward(see, newPosition);
					if(reward != 0){
						if(reward == 1){
							System.out.println("WIN");
						}
						done = true;
					}

					// Update Q-Table
					Optional<Map.Entry<Richtung, Double>> maxEntry = q_table.get(newPositionAsString).entrySet()
							.stream()
							.max(Map.Entry.comparingByValue());
					double maxValue = maxEntry.get().getValue();

					String positionAsString = koordinateToString(position);

					double q_table_value = q_table.get(positionAsString).get(action) * (1 - learningRate) +
							learningRate * (reward + discountRate * maxValue);
					q_table.get(positionAsString).put(action, q_table_value);

//				} catch (Exception e){
//					reward = -1;
//					done = true;
//				}

				// Transition
				reward_current_episode += reward;

				if(done){
					break;
				}
			}

			explorationRate = minExplorationRate + (maxExplorationRate - minExplorationRate) * Math.exp(-explorationDecayRate * episode);

			reward_per_episode[episode] = reward_current_episode;
		}

		// Finished learning - Print information
		printFancyQ_Table();

		int sum = 0;
		for (int i = 0; i < reward_per_episode.length; i++) {
			sum += reward_per_episode[i];

			if((i + 1) % 1000 == 0){
				System.out.println("Episode " + (i + 1) + " averages a reward of " + (double) sum / 1000);
				sum = 0;
			}
		}
		see.neustartSpielfigur();
	}

	private void stateValue(See see){
		double discountRate = 0.99; // Diminish
		// Mostly needed when transition probability isn't 1. Though here it breaks the loop when nothing got updated.
		double EPSILON = 0.005; // Threshold

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
			String koordinate = koordinateToString(state);

			ArrayList<Richtung> actions = getPossibleActions(state, size);
			int rnd = random.nextInt(actions.size());

			policy.put(koordinate,  actions.get(rnd));
		}

		// Initial value function
		for (Koordinate state :
				states) {
			String koordinate = koordinateToString(state);

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
				String koordinate = koordinateToString(state);

				// If terminal state
				if(getReward(see, state) != 0){
					continue;
				}

				double oldValue = valueFunction.get(koordinate);

				double newValue = 0;

				for (Richtung action :
						getPossibleActions(state, size)) {
					Koordinate stateAfterAction = new Koordinate(state.getZeile() + action.deltaZ(),state.getSpalte() + action.deltaS());

					double value = getReward(see, state) + discountRate * valueFunction.get(koordinateToString(stateAfterAction));

					if(value > newValue){
						newValue = value;

						policy.put(koordinate, action);
					}
				}

				valueFunction.put(koordinate, newValue);
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

		System.out.println(see.zustandAn(state));
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

	private String koordinateToString(Koordinate koordinate){
		String s = "";
		s += koordinate.getZeile();
		s += ";";
		s += koordinate.getSpalte();
		return s;
	}

	private void printFancyQ_Table(){
		DecimalFormat num = new DecimalFormat("0.00");
		num.setPositivePrefix("+");

		System.out.println("STATE |  HOCH   |  RECHTS |  RUNTER |  LINKS ");
		for (Map.Entry<String, Map<Richtung, Double>> row : q_table.entrySet()){
			System.out.print(row.getKey() + " ");
			for (Double value : row.getValue().values()){
				System.out.print("  |  " + num.format(value));
			}
			System.out.println();
		}
	}
}
