package frozenlake.pfadfinder.adrian;

import frozenlake.Koordinate;
import frozenlake.Richtung;
import frozenlake.See;
import frozenlake.Zustand;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Adrian Kaminski
 *
 * 2 Ansätze von State Value sind implementiert.
 * In der Methode lerneSee einfach den jeweiligen Ansatz auskommentieren und den anderen kommentieren.
 * Ein Ansatz kann Off- und On-Policy.
 *
 * Auf der Konsole werden Informationen zum Schluss ausgegeben.
 * z.B. die abschließende Policy und die Werte in der Value Function
 *
 * Q-Learning wurde ebenfalls implementiert funktioniert jedoch nur bei einfachen Seen,
 * da bei komplexen Seen das Ziel wahrscheinlich nicht erreicht wird und so keine Werte aktualisiert werden können.
 * Das gleiche Problem tritt bei State Value Off-Policy auf.
 */
public class Pfadfinder implements frozenlake.pfadfinder.IPfadfinder{

	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
	public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";
	public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";

	private Koordinate playerPosition;
	private boolean stateValue = false;

	private final Map<String, Double> valueFunction = new LinkedHashMap<>();
	private final Map<String, Richtung> policy = new LinkedHashMap<>();
	private final ArrayList<Koordinate> states = new ArrayList<>();

	private final Map<String, Map<Richtung, Double>> q_table = new LinkedHashMap<>();

	@Override
	public String meinName() {
		return "adrian";
	}

	@Override
	public boolean lerneSee(See see, boolean stateValue, boolean neuronalesNetz, boolean onPolicy) {
		if(stateValue){
			this.stateValue = true;
			//stateValueV2(see);
			stateValue(see, onPolicy);
		} else {
			Q_Learning(see);
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

	/**
	 * Klassischer Q-Learning Algorithmus
	 *
	 * @param see
	 */
	private void Q_Learning(See see) {
		int size = see.getGroesse();

		// Initialize Q-Table as map with coordinates as string representing the state
		// and the value as another map having all possible actions as key and the corresponding
		// Q-Table value with the state and the action as value.
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				Koordinate state = new Koordinate(i, j);
				String stateAsString = koordinateToString(state);

				Map<Richtung, Double> actions = new LinkedHashMap<>();

				for (Richtung action : Richtung.values()) {
					actions.put(action, 0.0);
				}

				q_table.put(stateAsString, actions);
			}
		}

		int numEpisodes = 10000;
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

			Koordinate state = new Koordinate(0,0);

			boolean done = false;
			double reward_current_episode = 0;

			for (int step = 0; step < maxStepsInEpisode; step++) {

				Richtung action = null;

				double exploration_rate_threshold = Math.random();
				if(exploration_rate_threshold > explorationRate){
					// Exploit
					double maxValue = Double.NEGATIVE_INFINITY;
					for (Map.Entry<Richtung, Double> rowState : q_table.get(koordinateToString(state)).entrySet()){
						if(rowState.getValue() > maxValue && getPossibleActions(state, size).contains(rowState.getKey())){
							maxValue = rowState.getValue();
							action = rowState.getKey();
						}
					}
				} else {
					// Explore
					ArrayList<Richtung> possibleActions = getPossibleActions(state, size);
					action = possibleActions.get(new Random().nextInt(possibleActions.size()));
				}

				// Take action
				double reward;

				see.geheNach(action);

				Koordinate newState = new Koordinate(state.getZeile() + action.deltaZ(), state.getSpalte() + action.deltaS());

				reward = getReward(see, newState);
				if(reward == 1 || reward == -1){
					done = true;
				}

				// Update Q-Table
				Optional<Map.Entry<Richtung, Double>> maxEntry = q_table.get(koordinateToString(newState)).entrySet()
						.stream()
						.max(Map.Entry.comparingByValue());
				Richtung maxAction = maxEntry.get().getKey();

				double q_table_value = q_table.get(koordinateToString(state)).get(action)
						* (1 - learningRate)
						+ learningRate
						* (reward + discountRate * q_table.get(koordinateToString(newState)).get(maxAction));
				q_table.get(koordinateToString(state)).replace(action, q_table_value);

				state = newState;

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

		int sum = 0;
		for (int i = 0; i < reward_per_episode.length; i++) {
			sum += reward_per_episode[i];

			if((i + 1) % 1000 == 0){
				System.out.println("Episode " + (i + 1) + " averages a reward of " + (double) sum / 1000);
				sum = 0;
			}
		}

		printFancyQ_Table();
		see.neustartSpielfigur();
	}

	/**
	 * Klassischer State Value Algorithmus
	 *
	 * @param see
	 * @param offPolicy true für off-Policy, false für on-Policy
	 */
	private void stateValue(See see, boolean offPolicy){
		int size = see.getGroesse();

		initialize(see);

		double discount_rate = 0.95;
		double learning_rate = 1;

		for (int episode = 0; episode < 10000; episode++) {
			see.neustartSpielfigur();

			Koordinate state = new Koordinate(see.spielerPosition().getZeile(), see.spielerPosition().getSpalte());

			for (int step = 0; step < size * size; step++) {
				Richtung action;
				if(offPolicy){
					ArrayList<Richtung> possibleActions = getPossibleActions(state, size);
					action = possibleActions.get(new Random().nextInt(possibleActions.size()));
				} else {
					action = getBestAction(state, see);
				}

				Zustand zustand = see.geheNach(action);

				Koordinate newState = new Koordinate(state.getZeile() + action.deltaZ(), state.getSpalte() + action.deltaS());

				double oldValue = valueFunction.get(koordinateToString(state));
				double reward = getReward(see, state);
				double valueNextState = valueFunction.get(koordinateToString(newState));

				double value = oldValue + learning_rate * (reward + discount_rate * valueNextState - oldValue);

				if(value > oldValue){
					valueFunction.put(koordinateToString(state), value);

					policy.put(koordinateToString(state), action);
				}

				state = new Koordinate(newState.getZeile(), newState.getSpalte());

				if(zustand == Zustand.Ziel || zustand == Zustand.Wasser){
					break;
				}
			}
		}
		see.neustartSpielfigur();

		System.out.println("Finished valueFunction with " + valueFunction.size() + " values:\n" + valueFunction + "\n");
		printFancyValueFunction(size);
		printFancyPolicy(size);
	}

	/**
	 * Initialisierung der Policy und Value Function, sowie die Bestimmung aller möglichen Zustände
	 *
	 * @param see
	 */
	private void initialize(See see){
		int size = see.getGroesse();

		// Define all possible states
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				states.add(new Koordinate(i, j));
			}
		}

		// Initial policy
		for (Koordinate state :
				states) {
			String koordinate = koordinateToString(state);

			ArrayList<Richtung> actions = getPossibleActions(state, size);
			int rnd = new Random().nextInt(actions.size());

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
	}

	/**
	 * Zweiter Ansatz der State Value Function.
	 *
	 * @param see
	 */
	private void stateValueV2(See see){
		int size = see.getGroesse();

		initialize(see);

		double discountRate = 0.99; // Diminish
		double EPSILON = 0.005; // Threshold

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

	/**
	 * Prüft, ob eine Aktion in einem Status zu einem validen Status führt
	 *
	 * @param position
	 * @param action
	 * @param size
	 * @return
	 */
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

	/**
	 * Gibt eine Liste für alle möglichen Aktionen für einen Status zurück
	 *
	 * @param position
	 * @param size als Seegröße
	 * @return Liste
	 */
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

	/**
	 * Gibt die Belohnung für einen Status zurück
	 *
	 * @param see
	 * @param state
	 * @return reward
	 */
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

	/**
	 * Schöne Darstellung einer Value Function
	 *
	 * @param size als Seegröße
	 */
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

	/**
	 * Schöne Darstellung einer Policy
	 *
	 * @param size als Seegröße
	 */
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

	/**
	 * Wandelt eine Koordinate in einen String um
	 *
	 * @param koordinate
	 * @return koordinate als String
	 */
	private String koordinateToString(Koordinate koordinate){
		String s = "";
		s += koordinate.getZeile();
		s += ";";
		s += koordinate.getSpalte();
		return s;
	}

	/**
	 * Schöne Darstellung für die Q-Table
	 */
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

	/**
	 * Gibt die beste Aktion für einen Status zurück. Falls mehrere beste Aktionen existieren, wird zufällig einer gewählt.
	 * Dies verhindert Endlosschleifen.
	 *
	 * @param state
	 * @param see
	 * @return beste Aktion
	 */
	private Richtung getBestAction(Koordinate state, See see){
		ArrayList<Richtung> possibleActions = getPossibleActions(state, see.getGroesse());

		double bestReward = Double.NEGATIVE_INFINITY;
		for (Richtung tmpAction : possibleActions){
			Koordinate tmpNewState = new Koordinate(state.getZeile() + tmpAction.deltaZ(), state.getSpalte() + tmpAction.deltaS());

			double reward = getReward(see, tmpNewState);
			if(reward > bestReward){
				bestReward = reward;
			}
		}

		ArrayList<Richtung> bestActions = new ArrayList<>();
		for (Richtung tmpAction : possibleActions){
			Koordinate tmpNewState = new Koordinate(state.getZeile() + tmpAction.deltaZ(), state.getSpalte() + tmpAction.deltaS());

			double reward = getReward(see, tmpNewState);
			if(reward >= bestReward){
				bestActions.add(tmpAction);
			}
		}

		return bestActions.get(new Random().nextInt(bestActions.size()));
	}
}
