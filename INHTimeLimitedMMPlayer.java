import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ggp.base.apps.player.Player;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

/**
 * Insert_Team_Name
 * Minimax Player
 * Searches entire move tree to play best move.
 *
 */
public class INHTimeLimitedMMPlayer extends GGPlayer {

	private long currTimeout = 0;
	private long TIME_LIMIT = 1000;
	private int lastBestScore = 0;
	private boolean timeIsUp = false;
	private ArrayList<MachineState> savedStates = new ArrayList<MachineState>();
	private ArrayList<Move> savedOrigMoves = new ArrayList<Move>();

	/**
	 * All we have to do here is call the Player's initialize method with
	 * our name as the argument so that the Player GUI knows which name to
	 * have selected at startup. This is all you need in the main method
	 * of your own player.
	 */
	public static void main(String[] args) {
		Player.initialize(new INHTimeLimitedMMPlayer().getName());
	}

	private long getTime() {
		return currTimeout - System.currentTimeMillis();
	}

	private int evalfn(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{

		if (!timeIsUp) {
			long timeLeft = getTime();
			if (timeLeft < TIME_LIMIT) {
				timeIsUp = true;
			}
		}
		return reward(role, state, machine);
		//return mobility(role, state, machine);
//		List<Role> opponents = findOpponents(role, machine);
//		Role opponent = opponents.get(0);
//		return 100 - reward(opponent, state, machine);
	}

	private int mobility(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		//double theirMoves = 0.0;
		//List<Role> opponents = findOpponents(role, machine);
		//Role opponent = opponents.get(0);
		List<Move> legalMoves = findLegals(role, state, machine);
		List<Move> feasibles = findActions(role, machine);
		double ourMoves = (double)(legalMoves.size()) / (double)(feasibles.size());


//
//		for (int i = 0; i < legalMoves.size(); i++) {
//			int result = minScore(role, state, machine, legalMoves.get(i));
//			List<Move> oppMoves = findLegals(opponent, state, machine);
//
//		}
		//System.out.println("Moves Available");
		//System.out.println(ourMoves);
		//System.out.println((int)(ourMoves * 100));
		return (int)(ourMoves * 100);
	}

	private int reward(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		return findReward(role, state, machine);
	}

	/**
	 * Currently, we can get along just fine by using the Prover State Machine.
	 * We will implement a more optimized PropNet State Machine later. The Cached
	 * State Machine is a wrapper that reduces the number of calls to the Prover
	 * State Machine by returning results of method calls that have been made previously.
	 * (e.g. getNextState calls or getLegalMoves for the same combination of parameters)
	 */
	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	/**
	 * If we wanted to use the metagame (or start) clock to compute something
	 * about the game (or explore the game tree), we could do so here. Since
	 * this is just a legal player, there is no need for such computation.
	 */
	@Override
	public void start(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

	}

	private int maxScore(Role role, MachineState state, StateMachine machine, int currDist, int maxDist, Move origMove)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		if (findTerminalp(state, machine)) {
			return findReward(role, state, machine);
		}
		long timeLeft = getTime();
		if (timeLeft < TIME_LIMIT || currDist == maxDist) {
			if (currDist == maxDist) {
				savedOrigMoves.add(origMove);
				savedStates.add(state);
			}
			return evalfn(role, state, machine);
		}

		List<Move> legalMoves = findLegals(role, state, machine);
		int score = 0;
		for (int i = 0; i < legalMoves.size(); i++) {
			int result = minScore(role, state, machine, legalMoves.get(i), currDist, maxDist, origMove);
			if (result > score) {
				score = result;
			}
			if (score == 100) {
				return 100;
			}
			if (timeIsUp) {
				return score;
			}
		}
		return score;
	}

	// Only works for 2 player games rn!
	private int minScore(Role role, MachineState state, StateMachine machine, Move action, int currDist, int maxDist, Move origMove)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		List<Role> opponents = findOpponents(role, machine);
		Role opponent = opponents.get(0);
		List<Move> legalMoves = findLegals(opponent, state, machine);
		int score = 100;
		for (int i = 0; i < legalMoves.size(); i++) {
			List<Move> currMove = new ArrayList<Move>();
			List<Role> roles = machine.getRoles();
			if (role.equals(roles.get(0))) {
				currMove.add(action);
				currMove.add(legalMoves.get(i));
			} else {
				currMove.add(legalMoves.get(i));
				currMove.add(action);
			}

			int result = maxScore(role, findNext(currMove, state, machine), machine, currDist + 1, maxDist, origMove);
			if (result < score) {
				score = result;
			}
			if (score <= 0) {
				return 0;
			}
			if (timeIsUp) {
				return score;
			}
		}
		return score;
	}

	private MachineState simulate(Move move, MachineState state, StateMachine machine)
			throws TransitionDefinitionException {
		List<Move> moveList = Arrays.asList(move);
		return findNext(moveList, state, machine);
	}

	private int CDMaxScore(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		if (findTerminalp(state, machine)) {
			return findReward(role, state, machine);
		}
		long timeLeft = getTime();
		if (timeLeft < TIME_LIMIT) {
			return evalfn(role, state, machine);
		}

		List<Move> legalMoves = findLegals(role, state, machine);
		int score = 0;
		for (int i = 0; i < legalMoves.size(); i++) {
			int result = CDMaxScore(role, simulate(legalMoves.get(i), state, machine), machine);
			if (result > score) {
				score = result;
			}
			if (score == 100) {
				return 100;
			}
		}
		return score;
	}

	private Move runCompulsive(long timeout, StateMachine machine, MachineState state, Role role)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		//Gets all legal moves for our player in the current state
		List<Move> legalMoves = findLegals(role, state, machine);

		Move currMove = legalMoves.get(0);
		int score = 0;
		Move chosenMove;
		for (int i = 0; i < legalMoves.size(); i++) {
			int result = CDMaxScore(role, simulate(legalMoves.get(i), state, machine), machine);
			if (result == 100) {
				chosenMove = legalMoves.get(i);
				//System.out.println("I am playing: " + chosenMove);
				return chosenMove;
			} else if (result > score){
				score = result;
				currMove = legalMoves.get(i);
			}
		}
		long timeLeft = getTime();
		System.out.println();
		System.out.println("Time Left: " + timeLeft);
		System.out.println("I am compulsively playing: " + currMove);
		return currMove;
	}

	///////
	private Move minimax(long timeout, Role role, MachineState state, StateMachine machine, int curr, int distance)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		//Gets all legal moves for our player in the current state
		List<Move> legalMoves = findLegals(role, state, machine);
		Move currMove = legalMoves.get(0);
		if (legalMoves.size() == 1) {
			return currMove;
		}
		int score = 0;
		for (int i = 0; i < legalMoves.size(); i++) {
			int result = minScore(role, state, machine, legalMoves.get(i), curr, distance, legalMoves.get(i));
			if (result > score){
				score = result;
				currMove = legalMoves.get(i);
			}
		}
		lastBestScore = score;
		System.out.println("I am playing: " + currMove);
		return currMove;
	}

	private Move iterativeDeepening(long timeout, Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		List<Move> xlegalMoves = findLegals(role, state, machine);
		Move bestMove = xlegalMoves.get(0);
		Move lastBestMove;
		savedStates = new ArrayList<MachineState>();
		savedOrigMoves = new ArrayList<Move>();

		if (xlegalMoves.size() == 1) {
			return bestMove;
		}
		for(int distance = 1; distance < 50000 && !timeIsUp; distance++) {
			System.out.println("Iterative deepening distance: " + distance);
			if (savedStates.size() > 0) {
				lastBestMove = bestMove;

				int bestScore = 0;

				ArrayList<MachineState> states = new ArrayList<MachineState>(savedStates);
				ArrayList<Move> moves = new ArrayList<Move>(savedOrigMoves);
				savedStates = new ArrayList<MachineState>();
				savedOrigMoves = new ArrayList<Move>();

				while (!states.isEmpty()) {
					if (timeIsUp) {
						return lastBestMove;
					}

					Move currMove = moves.get(0);
					MachineState currState = states.get(0);
					states.remove(0);
					moves.remove(0);

					List<Move> legalMoves = findLegals(role, currState, machine);
					int score = 0;
					for (int i = 0; i < legalMoves.size(); i++) {
						int result = minScore(role, currState, machine, legalMoves.get(i), distance - 1, distance, currMove);
						if (result > score) {
							score = result;
						}
						if (score == 100) {
							bestScore = 100;
							return currMove;
						}
						if (timeIsUp) {
							break;
						}
					}

					if (score >= bestScore) {
						bestScore = score;
						bestMove = currMove;
					}
					if (score >= lastBestScore) {
						lastBestMove = currMove;
						lastBestScore = score;
					}
				}


			} else {
				bestMove = minimax(timeout, role, state, machine, 0, distance);
			}
		}
		return bestMove;
	}
	/**
	 * Where your player selects the move they want to play. In-line comments
	 * explain each line of code. Your goal essentially boils down to returning the best
	 * move possible.
	 *
	 * The current state for the player is updated between moves automatically for you.
	 *
	 * The value of the timeout variable is the UNIX time by which you need to submit your move.
	 * You can determine how much time your player has left (in milliseconds) by using the following line of code:
	 * long timeLeft = timeout - System.currentTimeMillis();
	 *
	 * Make sure to submit your move before this time runs out. It's also a good
	 * idea to leave a couple seconds (2-4) as buffer for network lag/spikes and
	 * so that you don't overrun your time thus timing out (which plays
	 * a random move for you and counts as an error -- two very bad things).
	 */
	@Override
	public Move play(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		//Gets our state machine (the same one as returned in getInitialStateMachine)
		//This State Machine simulates the game we are currently playing.
		StateMachine machine = getStateMachine();

		//Gets the current state we're in (e.g. move 2 of a game of tic tac toe where X just played in the center)
		MachineState state = getCurrentState();
		//Gets our role (e.g. X or O in a game of tic tac toe)
		Role role = getRole();
		currTimeout = timeout;
		timeIsUp = false;

		// Determine Player
		List<Role> opponents = findOpponents(role, machine);
		if (opponents.size() == 0) {
			return runCompulsive(timeout, machine, state, role);
		} else {//if (opponents.size() >= 1) {
			return iterativeDeepening(timeout, role, state, machine);
		}
	}

	/**
	 * Can be used for cleanup at the end of a game, if it is needed.
	 */
	@Override
	public void stop() {

	}

	/**
	 * Can be used for cleanup in the event a game is aborted while
	 * still in progress, if it is needed.
	 */
	@Override
	public void abort() {

	}

	/**
	 * Returns the name of the player.
	 */
	@Override
	public String getName() {
		return "INHTimeLimitedMM_player";
	}


//	private Move runBFSMinmax(long timeout, StateMachine machine, MachineState state, Role role)
//			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
//		// Prep three lists to keep track of moves, original moves, and states to consider.
//		ArrayList<Move> legalMoves = new ArrayList<Move>(findLegals(role, state, machine));
//		ArrayList<Move> origMoves = new ArrayList<Move>(findLegals(role, state, machine));
//		ArrayList<MachineState> states = new ArrayList<MachineState>();
//		//ArrayList<LinkedList<Move>> hashTable = new ArrayList<>();
//		//ArrayList<Role> roles = new ArrayList<Role>();
//		// Fill States Array
//		for (int i = 0; i < legalMoves.size(); i++) {
//			states.add(state);
//			//roles.add(role);
//		}
//
//		// These track which variables we are currently on in our arraylists
//		Move currMove = legalMoves.get(0);
//		MachineState currState = states.get(0);
//		Move origMove = origMoves.get(0);
//		//Role currRole = roles.get(0);
//
//		// ChosenMove is the move we return
//		Move chosenMove = origMoves.get(0);
//
//		// Track best score move so far
//		int score = 0;
//
//		// Get Opponent
//		List<Role> opponents = findOpponents(role, machine);
//		Role opponent = opponents.get(0);
//
//		// BFS: While we still have legal moves, we do a round of minimax
//		WHILE_LOOP:
//		while (legalMoves.size() > 0) {
//
//			// get the next set of currMove, currState, and the original move leading to this
//			currMove = legalMoves.get(0);
//			currState = states.get(0);
//			origMove = origMoves.get(0);
//			//currRole = roles.get(0);
//
//			// My turn, I want to MAXIMIZE my score
//			// If time is up, just get the value of this state
//			if (timeIsUp == true) {
//				break WHILE_LOOP;
//			}
//
//			// Get noop move
//			Move noop = findLegals(opponent, currState, machine).get(0);
//
//			// Make our move first
//			List<Move> currMoveSet = new ArrayList<Move>();
//			currMoveSet.add(currMove);
//			currMoveSet.add(noop);
//			MachineState nState = findNext(currMoveSet, currState, machine);
//
//			// assuming we just made currMove, we are now in nState
//			int currScore = -1;
//
//			if (findTerminalp(nState, machine)) {
//				currScore = findReward(role, nState, machine);
//			} else {
//				// Now get opponents moves
//				List<Move> oppMoves = findLegals(opponent, nState, machine);
//
//				// MINSCORE Step - for each opponent move given our current move
//				FOR_LOOP:
//				for (int i = 0; i < oppMoves.size(); i++) {
//					if (timeIsUp) {
//						break FOR_LOOP;
//					}
//					// Get the moveSet to pass to the "Max" score step
//					currMoveSet = new ArrayList<Move>();
//					currMoveSet.add(noop);
//					currMoveSet.add(oppMoves.get(i));
//
//					/////////////////// MAX SCORE STEP  /////////////////////
//					MachineState nextState = findNext(currMoveSet, nState, machine);
//					int result = -1;
//					boolean terminal = false;
//					//long timeLeft = getTime();
//
//					// Find score for me if opponent does this move
//					if (findTerminalp(nextState, machine)) {
//						result = findReward(role, nextState, machine);
//						terminal = true;
//					} else {
//						result = evalfn(role, nextState, machine);
//					}
//
//					// Opponent wants to screw me over, so they want to minimize currScore
//					if (currScore == -1 || result < currScore) {
//						currScore = result;
//					}
//
//					if (currScore <= 0) {
//						currScore = 0;
//						break FOR_LOOP;
//					}
//
//					if (timeIsUp) {
//						break FOR_LOOP;
//					}
//
//					// If result == -1, that means we aren't terminal or out of time. Add to back of List
//					if (!terminal) {
//						ArrayList<Move> nextMoves = new ArrayList<Move>(findLegals(role, nextState,machine));
//						legalMoves.addAll(nextMoves);
//						for (int j = 0; j < nextMoves.size(); j++) {
//							states.add(nextState);
//							origMoves.add(origMove);
//						}
//					}
//				}	// end of for loop
//			}
//			legalMoves.remove(0);
//			states.remove(0);
//			origMoves.remove(0);
//			if (currScore != -1 && currScore > score){
//				System.out.println("New best move");
//				System.out.println(currScore);
//				score = currScore;
//				chosenMove = origMove;
//			}
//		}
//		long timeLeft = getTime();
//		System.out.println();
//		System.out.println("Time Left: " + timeLeft);
//		System.out.println("Score: " + score);
//		System.out.println("I am playing: " + chosenMove);
//		return chosenMove;
//	}


//	private Move runMinmax(long timeout, StateMachine machine, MachineState state, Role role)
//			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
//		//Gets all legal moves for our player in the current state
//		List<Move> legalMoves = findLegals(role, state, machine);
//		Move currMove = legalMoves.get(0);
//		int score = 0;
//		for (int i = 0; i < legalMoves.size(); i++) {
//			System.out.println("Analyzing Move " + (i + 1) + " of " + legalMoves.size());
//			int result = minScore(role, state, machine, legalMoves.get(i));
//			if (result > score){
//				score = result;
//				currMove = legalMoves.get(i);
//			}
//		}
//		long timeLeft = getTime();
//		System.out.println();
//		System.out.println("Time Left: " + timeLeft);
//		System.out.println("Score: " + score);
//		System.out.println("I am playing: " + currMove);
//		return currMove;
//	}


}
