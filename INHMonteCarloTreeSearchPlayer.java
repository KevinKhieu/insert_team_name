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
 * Monte Carlo Tree Search Player
 * Searches entire move tree to play best move.
 *
 */
public class INHMonteCarloTreeSearchPlayer extends GGPlayer {

	class Node {
		public Role playerRole;  // Our role (not the role of the player who's going next)
		public Move previousPlayerMove;  // Move made by our player previously

		public boolean expanded = false;  // Meant for checking whether we should expand on node
		public double utility = 0;
		public int visits = 0;

		public Node parent;
		public List<Node> children;

		// Information about the game
		public MachineState state;
		public StateMachine machine;


		public Node(Node parent, Role playerRole, Move previousPlayerMove, MachineState state, StateMachine machine) {
			this.playerRole = playerRole;
			this.previousPlayerMove = previousPlayerMove;

			this.parent = parent;
			this.children = new ArrayList<Node>();

			this.state = state;
			this.machine = machine;
		}
	}

	private long TIME_LIMIT = 3000;
	private long currTimeout = 0;
	private int numSimulations = 10;

	private boolean doWeHaveTime() {
		return (currTimeout - System.currentTimeMillis()) > TIME_LIMIT;
	}

	private void expand(Node node) throws MoveDefinitionException, TransitionDefinitionException {
		if (node.expanded) {
			return;
		}
		List<Move> legalMoves = findLegals(node.playerRole, node.state, node.machine);
		for (int i = 0; i < legalMoves.size(); i++) {
			List<List<Move>> possibleJointMoves = findLegalJoints(node.playerRole, legalMoves.get(i), node.state, node.machine);
			for (int j = 0; j < possibleJointMoves.size(); j++) {
				node.children.add(new Node(node, node.playerRole, legalMoves.get(i), findNext(possibleJointMoves.get(j), node.state, node.machine), node.machine));
			}
		}
		node.expanded = true;
	}

	private double selectFn(Node node) {
		return node.utility / node.visits + Math.sqrt(2 * Math.log(node.parent.visits) / node.visits);
	}

	private Node select(Node node) throws MoveDefinitionException, TransitionDefinitionException {
		if (node == null || findTerminalp(node.state, node.machine)) {
			return null;
		}
		if (node.visits == 0) {
			return node;
		}
		for (int i = 0; i < node.children.size(); i++) {
			Node childNode = node.children.get(i);
			if (childNode.visits == 0 && !findTerminalp(childNode.state, childNode.machine)) {
				return childNode;
			}
		}
		double score = -1;
		Node result = null;
		for (int i = 0; i < node.children.size(); i++) {
			Node childNode = node.children.get(i);
			double newScore = selectFn(childNode);
			if (newScore > score && !findTerminalp(childNode.state, childNode.machine)) {
				score = newScore;
				result = childNode;
			}

		}
		return select(result);
	}

	private void backpropagate(Node node, double score) {
		node.visits++;
		node.utility += score;
		if (node.parent != null) {
			backpropagate(node.parent, score);
		}
	}

	private double simulate(Node node, int count)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		double total = 0;
		for (int i = 0; i < count; i++) {
			total = total + depthCharge(node.playerRole, node.state, node.machine);
		}
		return total / count;
	}

	/**
	 * Number of levels we are allowed to search.
	 */
	private int limit = 9;

	/**
	 * All we have to do here is call the Player's initialize method with
	 * our name as the argument so that the Player GUI knows which name to
	 * have selected at startup. This is all you need in the main method
	 * of your own player.
	 */
	public static void main(String[] args) {
		Player.initialize(new INHMonteCarloTreeSearchPlayer().getName());
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
		if (legalMoves.size() == 1) {
			System.out.println("I am playing: " + legalMoves.get(0));
			return legalMoves.get(0);
		}

		//Begin MCTS
		Node rootNode = new Node(null, role, null, state, machine);
		this.currTimeout = timeout;
		while (doWeHaveTime()) {
			Node selectedNode = select(rootNode);
			if (selectedNode == null) {
				break;
			}
			expand(selectedNode);
			double score = simulate(selectedNode, this.numSimulations);
			backpropagate(selectedNode, score);
		}
		Move bestMove = findLegals(role, state, machine).get(0);
		double bestScore = 0.0;
		for (int i = 0; i < rootNode.children.size(); i++) {
			Node childNode = rootNode.children.get(i);
			if (childNode.visits > 0) {
				double score = childNode.utility / childNode.visits;
				System.out.println("Utility considered: " + childNode.utility);
				System.out.println("Visits: " + childNode.visits);
				System.out.println("Scaled utility considered: " + score);
				if (score > bestScore) {
					bestScore = score;
					bestMove = childNode.previousPlayerMove;
				}
			}
		}

		System.out.println("I am playing: " + bestMove);
		return bestMove;
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
		return "INHMonteCarloTreeSearch_player";
	}



}
