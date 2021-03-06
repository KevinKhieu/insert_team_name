import java.util.ArrayList;
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
public class INHMinimaxPlayer extends GGPlayer {

	/**
	 * All we have to do here is call the Player's initialize method with
	 * our name as the argument so that the Player GUI knows which name to
	 * have selected at startup. This is all you need in the main method
	 * of your own player.
	 */
	public static void main(String[] args) {
		Player.initialize(new INHMinimaxPlayer().getName());
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

	private MachineState simulate(Move move, MachineState state, StateMachine machine)
			throws TransitionDefinitionException {
		List<Move> moveList = new ArrayList<Move>();
		moveList.add(move);
		Move noop = Move.create("noop");
		moveList.add(noop);
		return findNext(moveList, state, machine);
	}

	private int maxScore(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException{
		if (findTerminalp(state, machine)) {
			return findReward(role, state, machine);
		}

		List<Move> legalMoves = findLegals(role, state, machine);
		int score = 0;
		for (int i = 0; i < legalMoves.size(); i++) {
			int result = minScore(role, state, machine, legalMoves.get(i));
			if (result > score) {
				score = result;
			}
		}
		return score;
	}

	// Only works for 2 player games rn!
	private int minScore(Role role, MachineState state, StateMachine machine, Move action)
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

			int result = maxScore(role, findNext(currMove, state, machine), machine);
			if (result < score) {
				score = result;
			}
		}
		return score;
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

		//Gets all legal moves for our player in the current state
		List<Move> legalMoves = findLegals(role, state, machine);
		Move currMove = legalMoves.get(0);
		int score = 0;
		for (int i = 0; i < legalMoves.size(); i++) {
			int result = minScore(role, state, machine, legalMoves.get(i));
			if (result > score){
				score = result;
				currMove = legalMoves.get(i);
			}
		}
		System.out.println("I am playing: " + currMove);
		return currMove;
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
		return "INHminimax_player";
	}



}
