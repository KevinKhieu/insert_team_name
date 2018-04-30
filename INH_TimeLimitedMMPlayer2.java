import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
public class INH_TimeLimitedMMPlayer2 extends GGPlayer {

	private long currTimeout = 0;
	private long TIME_LIMIT = 500;
	private double bestSavedScore = 0;
	private int bestSavedDepth = 0;
	private int minDist = 3;
	private boolean timeIsUp = false;
	private Move bestSavedMove;
	private List<Role> opponents;
	private List<Role> roles;
	private Role opponent;

	/**
	 * All we have to do here is call the Player's initialize method with
	 * our name as the argument so that the Player GUI knows which name to
	 * have selected at startup. This is all you need in the main method
	 * of your own player.
	 */
	public static void main(String[] args) {
		Player.initialize(new INH_TimeLimitedMMPlayer2().getName());
	}

	private long getTime() {
		return currTimeout - System.currentTimeMillis();
	}

	private double evalfn(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{

		if (!timeIsUp) {
			long timeLeft = getTime();
			if (timeLeft < TIME_LIMIT) {
				timeIsUp = true;
			}
		}
		double myReward = reward(role, state, machine);
		//double val1 = myReward - reward(opponent, state, machine);
		double val2 = mobility(role, state, machine) - mobility(opponent, state, machine);
		double val = myReward + val2;
		if (val < 0) {
			val = 0;
		}
		if (val > 100) {
			val = 100;
		}
		return val;
	}

	private double evalfnCD(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{

		if (!timeIsUp) {
			long timeLeft = getTime();
			if (timeLeft < TIME_LIMIT) {
				timeIsUp = true;
			}
		}
		double val = reward(role, state, machine) + mobility(role, state, machine);
		if (val < 0) {
			val = 0;
		}
		if (val > 100) {
			return 100;
		}
		return val;
	}

	private double mobility(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		List<Move> legalMoves = findLegals(role, state, machine);
		List<Move> feasibles = findActions(role, machine);
		double ourMoves = (double)(legalMoves.size()) / (double)(feasibles.size());
		return (double)(ourMoves * 100);
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

	private double maxScore(Role role, MachineState state, StateMachine machine, int currDist, int maxDist, Move origMove, double alpha, double beta)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		if (findTerminalp(state, machine)) {
			return findReward(role, state, machine);
		}

		long timeLeft = getTime();
		if (timeLeft < TIME_LIMIT || currDist == maxDist) {
			double val = evalfn(role, state, machine);
			return val;
		}

		List<Move> legalMoves = findLegals(role, state, machine);

		for (int i = 0; i < legalMoves.size(); i++) {
			double result = minScore(role, state, machine, legalMoves.get(i), currDist + 1, maxDist, origMove, alpha, beta);
			if (result > alpha) {
				alpha = result;
			}
			if (alpha >= beta) {
				return beta;
			}
		}
		return alpha;//score;
	}

	// Only works for 2 player games rn!
	private double minScore(Role role, MachineState state, StateMachine machine, Move action, int currDist, int maxDist, Move origMove, double alpha, double beta)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{

		// get Opponents moves
		List<Move> legalMoves = findLegals(opponent, state, machine);

		// For each move, see which one minimizes our score
		for (int i = 0; i < legalMoves.size(); i++) {

			List<Move> currMove = new ArrayList<Move>();
			if (role.equals(roles.get(0))) {
				currMove.add(action);
				currMove.add(legalMoves.get(i));
			} else {
				currMove.add(legalMoves.get(i));
				currMove.add(action);
			}

//			currMove.add(action);
//			currMove.add(legalMoves.get(i));

//			List<Move> currMove = new ArrayList<Move>();
//			currMove.add(action);
//			currMove.add(legalMoves.get(i));

			double result = maxScore(role, findNext(currMove, state, machine), machine, currDist, maxDist, origMove, alpha, beta);
			if (result < beta) {
				beta = result;
			}
			if (beta <= alpha) {
				return alpha;
			}
		}
		return beta; // score
	}

	private MachineState simulate(Move move, MachineState state, StateMachine machine)
			throws TransitionDefinitionException {
		List<Move> moveList = Arrays.asList(move);
		return findNext(moveList, state, machine);
	}

	private double CDMaxScore(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		if (findTerminalp(state, machine)) {
			return findReward(role, state, machine);
		}
		long timeLeft = getTime();
		if (timeLeft < TIME_LIMIT) {
			double val = evalfnCD(role, state, machine);
			return val;
		}

		List<Move> legalMoves = findLegals(role, state, machine);
		double score = 0;
		for (int i = 0; i < legalMoves.size(); i++) {
			double result = CDMaxScore(role, simulate(legalMoves.get(i), state, machine), machine);
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
		double score = 0;
		Move chosenMove;
		for (int i = 0; i < legalMoves.size(); i++) {
			double result = CDMaxScore(role, simulate(legalMoves.get(i), state, machine), machine);
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

	private Move minimax(long timeout, Role role, MachineState state, StateMachine machine, int curr, int distance)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		//Gets all legal moves for our player in the current state
		ArrayList<Move> legalMoves = new ArrayList<Move>(findLegals(role, state, machine));
		Move bestMove = legalMoves.get(0);
		Move currMove = bestMove;

		// In the case of noop, just return the noop
		if (legalMoves.size() == 1) {
			return bestMove;
		}
		Collections.shuffle(legalMoves);

		double score = 0;
		for (int i = 0; i < legalMoves.size(); i++) {
			currMove = legalMoves.get(i);
			double result = minScore(role, state, machine, currMove, curr, distance + 1, currMove, 0, 100);
			if (result > score){
				score = result;
				bestMove = currMove;
				if (result == 100) {
					break;
				}
			}
			if (result == score) {
				Random rand = new Random();
				double index = rand.nextInt(2);
				if (index != 0) {
					bestMove = currMove;
				}
			}

		}

		// If score is winning and we can win faster...
		// or
		// If score is same as best so far, but we look further...
		// or
		// if score is better than our best saved score, but we've looked far ahead enough to not be naive
		//if ((score == 100 && distance < bestSavedDepth) || (score == bestSavedScore && score != 100 && distance < bestSavedDepth) ||
			//	(score > bestSavedScore && distance > minDist)) {
		if ((score == 100 && (bestSavedScore != 100 || distance < bestSavedDepth)) || (score > bestSavedScore && distance > minDist)) {
			bestSavedScore = score;
			bestSavedMove = bestMove;
			bestSavedDepth = distance;
		}
		return bestSavedMove;
	}

	private Move iterativeDeepening(long timeout, Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		List<Move> xlegalMoves = findLegals(role, state, machine);
		Move bestMove = xlegalMoves.get(0);
		bestSavedScore = 0;
		bestSavedDepth = 0;
		if (xlegalMoves.size() == 1) {
			return bestMove;
		}
		for (int distance = 1; distance < 700 && !timeIsUp; distance++) {
			bestMove = minimax(timeout, role, state, machine, 0, distance);
			if (bestSavedScore == 100) {
				System.out.println("Best Saved Score is 100!");
				System.out.println("I am playing: " + bestSavedMove);
				System.out.println("Best Saved Depth: " + bestSavedDepth);
				return bestSavedMove;
			}
		}
		System.out.println("Best Saved Score: " + bestSavedScore);
		System.out.println("Best Saved Depth: " + bestSavedDepth);
		System.out.println("I am playing: " + bestMove);
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
		roles = machine.getRoles();
		currTimeout = timeout;
		timeIsUp = false;

		// Determine Player
		opponents = findOpponents(role, machine);
		if (opponents.size() == 0) {
			return runCompulsive(timeout, machine, state, role);
		} else {//if (opponents.size() >= 1) {
			opponent = opponents.get(0);
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
		return "INH_TimeLimited_AlphaBetaPlayer";
	}
}
