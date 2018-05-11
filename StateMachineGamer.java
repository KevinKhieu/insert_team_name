package org.ggp.base.player.gamer.statemachine;

import java.util.ArrayList;
import java.util.List;

import org.ggp.base.player.gamer.Gamer;
import org.ggp.base.player.gamer.exception.AbortingException;
import org.ggp.base.player.gamer.exception.MetaGamingException;
import org.ggp.base.player.gamer.exception.MoveSelectionException;
import org.ggp.base.player.gamer.exception.StoppingException;
import org.ggp.base.util.gdl.GdlUtils;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlDistinct;
import org.ggp.base.util.gdl.grammar.GdlFunction;
import org.ggp.base.util.gdl.grammar.GdlLiteral;
import org.ggp.base.util.gdl.grammar.GdlNot;
import org.ggp.base.util.gdl.grammar.GdlOr;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlProposition;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlRule;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.gdl.grammar.GdlVariable;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.prover.aima.substitution.Substitution;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;


/**
 * The base class for Gamers that rely on representing games as state machines.
 * Almost every player should subclass this class, since it provides the common
 * methods for interpreting the match history as transitions in a state machine,
 * and for keeping an up-to-date view of the current state of the game.
 *
 * See @SimpleSearchLightGamer, @HumanGamer, and @RandomGamer for examples.
 *
 * @author evancox
 * @author Sam
 */
public abstract class StateMachineGamer extends Gamer
{
    // =====================================================================
    // First, the abstract methods which need to be overridden by subclasses.
    // These determine what state machine is used, what the gamer does during
    // metagaming, and how the gamer selects moves.

    /**
     * Defines which state machine this gamer will use.
     * @return
     */
    public abstract StateMachine getInitialStateMachine();

    /**
     * Defines the metagaming action taken by a player during the START_CLOCK
     * @param timeout time in milliseconds since the era when this function must return
     * @throws TransitionDefinitionException
     * @throws MoveDefinitionException
     * @throws GoalDefinitionException
     */
    public abstract void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException;

    /**
     * Defines the algorithm that the player uses to select their move.
     * @param timeout time in milliseconds since the era when this function must return
     * @return Move - the move selected by the player
     * @throws TransitionDefinitionException
     * @throws MoveDefinitionException
     * @throws GoalDefinitionException
     */
    public abstract Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException;

    /**
     * Defines any actions that the player takes upon the game cleanly ending.
     */
    public abstract void stateMachineStop();

    /**
     * Defines any actions that the player takes upon the game abruptly ending.
     */
    public abstract void stateMachineAbort();

    // =====================================================================
    // Next, methods which can be used by subclasses to get information about
    // the current state of the game, and tweak the state machine on the fly.

	/**
	 * Returns the current state of the game.
	 */
	public final MachineState getCurrentState()
	{
		return currentState;
	}

	/**
	 * Returns the role that this gamer is playing as in the game.
	 */
	public final Role getRole()
	{
		return role;
	}

	/**
	 * Returns the state machine.  This is used for calculating the next state and other operations, such as computing
	 * the legal moves for all players, whether states are terminal, and the goal values of terminal states.
	 */
	public final StateMachine getStateMachine()
	{
		return stateMachine;
	}

    /**
     * Cleans up the role, currentState and stateMachine. This should only be
     * used when a match is over, and even then only when you really need to
     * free up resources that the state machine has tied up. Currently, it is
     * only used in the Proxy, for players designed to run 24/7.
     */
    protected final void cleanupAfterMatch() {
        role = null;
        currentState = null;
        stateMachine = null;
        setMatch(null);
        setRoleName(null);
    }

    /**
     * Switches stateMachine to newStateMachine, playing through the match
     * history to the current state so that currentState is expressed using
     * a MachineState generated by the new state machine.
     *
     * This is not done in a thread-safe fashion with respect to the rest of
     * the gamer, so be careful when using this method.
     *
     * @param newStateMachine the new state machine
     */
    protected final void switchStateMachine(StateMachine newStateMachine) {
        try {
            MachineState newCurrentState = newStateMachine.getInitialState();
            Role newRole = newStateMachine.getRoleFromConstant(getRoleName());

            // Attempt to run through the game history in the new machine
            List<List<GdlTerm>> theMoveHistory = getMatch().getMoveHistory();
            for(List<GdlTerm> nextMove : theMoveHistory) {
                List<Move> theJointMove = new ArrayList<Move>();
                for(GdlTerm theSentence : nextMove)
                    theJointMove.add(newStateMachine.getMoveFromTerm(theSentence));
                newCurrentState = newStateMachine.getNextStateDestructively(newCurrentState, theJointMove);
            }

            // Finally, switch over if everything went well.
            role = newRole;
            currentState = newCurrentState;
            stateMachine = newStateMachine;
        } catch (Exception e) {
            GamerLogger.log("GamePlayer", "Caught an exception while switching state machine!");
            GamerLogger.logStackTrace("GamePlayer", e);
        }
    }

    /**
     * A function that can be used when deserializing gamers, to bring a
     * state machine gamer back to the internal state that it has when it
     * arrives at a particular game state.
     */
	public final void resetStateFromMatch() {
        stateMachine = getInitialStateMachine();
        stateMachine.initialize(getMatch().getGame().getRules());
        currentState = stateMachine.getMachineStateFromSentenceList(getMatch().getMostRecentState());
        role = stateMachine.getRoleFromConstant(getRoleName());
	}

	private List<Gdl> pruneRules(List<Gdl> rules) {
		List<Gdl> newRules = new ArrayList<Gdl>();
		for (int i=0; i<rules.size(); i++) {
			if (!subsumedp(rules.get(i), newRules) &&
	           !subsumedp(rules.get(i),rules.subList(i+1, rules.size()))) {
				newRules.add(rules.get(i));
			}
		}
		return newRules;
	}

	private boolean subsumedp(Gdl rule, List<Gdl> rules) {
		for (int i=0; i<rules.size(); i++) {
			if (subsumesP(rules.get(i), rule)) {
				return true;
			}
		}
	  return false;
	}

	// If the first expression can be made to look like the second by binding
	// the variables in the first expression, then the method returns a binding
	// list for those variables; otherwise, it returns false.
	private Substitution matcher(Gdl p, Gdl q) {
		List<GdlVariable> pVars = GdlUtils.getVariables(p);
		List<GdlVariable> qVars = GdlUtils.getVariables(q);

		if (pVars.size() == 0 || qVars.size() == 0 || pVars.size() != qVars.size()) {
			return null;
		}
		Substitution theta = new Substitution();
		for (int i = 0; i < pVars.size(); i++) {
			theta.put(pVars.get(i), qVars.get(i));
		}

		if (p instanceof GdlLiteral && q instanceof GdlLiteral) {
			GdlLiteral pNew = substitute((GdlLiteral)p, theta);
			GdlLiteral qNew = substitute((GdlLiteral)q, theta);
			if (pNew.equals(qNew)) {
				return theta;
			}
		}
		return null;
	}

	// does the same thing as matcher but starts with the bindings on the
	// given binding list al
	private Substitution match(GdlLiteral p, GdlLiteral q, Substitution al) {

		List<GdlVariable> pVars = GdlUtils.getVariables(p);
		List<GdlVariable> qVars = GdlUtils.getVariables(q);

		if (pVars.size() == 0 || qVars.size() == 0 || pVars.size() != qVars.size()) {
			return null;
		}
		for (int i = 0; i < pVars.size(); i++) {
			if (!al.contains(pVars.get(i))) {
				al.put(pVars.get(i), qVars.get(i));
			}
		}
		if (p instanceof GdlLiteral && q instanceof GdlLiteral) {
			GdlLiteral pNew = substitute((GdlLiteral)p, al);
			GdlLiteral qNew = substitute((GdlLiteral)q, al);
			if (pNew.equals(qNew)) {
				return al;
			}
		}
		return null;
	}

	private boolean subsumesP(Gdl p, Gdl q) {
		if (p.equals(q)) {
			return true;
		}
		if ((p instanceof GdlConstant) || (q instanceof GdlConstant)) {
            return false;
        }
		else if (p instanceof GdlRule && q instanceof GdlRule) {
			GdlRule ruleP = (GdlRule)p;
			GdlRule ruleQ = (GdlRule)q;

			Substitution al = matcher(ruleP.getHead(), ruleQ.getHead());
			List<GdlLiteral> rulePBody = ruleP.getBody();
			List<GdlLiteral> ruleQBody = ruleQ.getBody();

			if (al != null && subsumesExp(rulePBody, ruleQBody, al)) {
				return true;
			}
		}
		return false;
	}

	private boolean subsumesExp(List<GdlLiteral> pl, List<GdlLiteral> ql, Substitution al) {
		if (pl.size() == 0) {
			return true;
		}
		for (int i=0; i<ql.size(); i++) {
			Substitution bl = match(pl.get(0), ql.get(i), al);
			if (bl != null && subsumesExp(pl.subList(1, pl.size()), ql, bl)) {
				return true;
			}
		}
		return false;
	}

	public static GdlLiteral substitute(GdlLiteral literal, Substitution theta) {
		return substituteLiteral(literal, theta);
	}

	public static GdlSentence substitute(GdlSentence sentence, Substitution theta) {
		return substituteSentence(sentence, theta);
	}

	public static GdlRule substitute(GdlRule rule, Substitution theta) {
		return substituteRule(rule, theta);
	}

	private static GdlConstant substituteConstant(GdlConstant constant, Substitution theta) {
		return constant;
	}

	private static GdlDistinct substituteDistinct(GdlDistinct distinct, Substitution theta) {
		if (distinct.isGround()) {
			return distinct;
		}
		else {
			GdlTerm arg1 = substituteTerm(distinct.getArg1(), theta);
			GdlTerm arg2 = substituteTerm(distinct.getArg2(), theta);
			return GdlPool.getDistinct(arg1, arg2);
		}
	}

	private static GdlFunction substituteFunction(GdlFunction function, Substitution theta)
	{
		if (function.isGround()) {
			return function;
		}
		else {
			GdlConstant name = substituteConstant(function.getName(), theta);

			List<GdlTerm> body = new ArrayList<GdlTerm>();
			for (int i = 0; i < function.arity(); i++) {
				body.add(substituteTerm(function.get(i), theta));
			}

			return GdlPool.getFunction(name, body);
		}
	}

	private static GdlLiteral substituteLiteral(GdlLiteral literal, Substitution theta) {
		if (literal instanceof GdlDistinct) {
			return substituteDistinct((GdlDistinct) literal, theta);
		}
		else if (literal instanceof GdlNot) {
			return substituteNot((GdlNot) literal, theta);
		}
		else if (literal instanceof GdlOr) {
			return substituteOr((GdlOr) literal, theta);
		}
		else {
			return substituteSentence((GdlSentence) literal, theta);
		}
	}

	private static GdlNot substituteNot(GdlNot not, Substitution theta) {
		if (not.isGround()) {
			return not;
		}
		else {
			GdlLiteral body = substituteLiteral(not.getBody(), theta);
			return GdlPool.getNot(body);
		}
	}

	private static GdlOr substituteOr(GdlOr or, Substitution theta) {
		if (or.isGround()) {
			return or;
		}
		else {
			List<GdlLiteral> disjuncts = new ArrayList<GdlLiteral>();
			for (int i = 0; i < or.arity(); i++) {
				disjuncts.add(substituteLiteral(or.get(i), theta));
			}

			return GdlPool.getOr(disjuncts);
		}
	}

	private static GdlProposition substituteProposition(GdlProposition proposition, Substitution theta) {
		return proposition;
	}

	private static GdlRelation substituteRelation(GdlRelation relation, Substitution theta) {
		if (relation.isGround()) {
			return relation;
		}
		else {
			GdlConstant name = substituteConstant(relation.getName(), theta);

			List<GdlTerm> body = new ArrayList<GdlTerm>();
			for (int i = 0; i < relation.arity(); i++) {
				body.add(substituteTerm(relation.get(i), theta));
			}

			return GdlPool.getRelation(name, body);
		}
	}

	private static GdlSentence substituteSentence(GdlSentence sentence, Substitution theta) {
		if (sentence instanceof GdlProposition) {
			return substituteProposition((GdlProposition) sentence, theta);
		}
		else {
			return substituteRelation((GdlRelation) sentence, theta);
		}
	}

	private static GdlTerm substituteTerm(GdlTerm term, Substitution theta) {
		if (term instanceof GdlConstant) {
			return substituteConstant((GdlConstant) term, theta);
		}
		else if (term instanceof GdlVariable) {
			return substituteVariable((GdlVariable) term, theta);
		}
		else {
			return substituteFunction((GdlFunction) term, theta);
		}
	}

	private static GdlTerm substituteVariable(GdlVariable variable, Substitution theta) {
		if (!theta.contains(variable)) {
			return variable;
		}
		else {
			GdlTerm result = theta.get(variable);
			theta.put(variable, result);
			return result;
		}
	}

	private static GdlRule substituteRule(GdlRule rule, Substitution theta) {
		GdlSentence head = substitute(rule.getHead(), theta);

		List<GdlLiteral> body = new ArrayList<GdlLiteral>();
		for ( GdlLiteral literal : rule.getBody() ) {
			body.add(substituteLiteral(literal, theta));
		}

		return GdlPool.getRule(head, body);
	}

    // =====================================================================
    // Finally, methods which are overridden with proper state-machine-based
	// semantics. These basically wrap a state-machine-based view of the world
	// around the ordinary metaGame() and selectMove() functions, calling the
	// new stateMachineMetaGame() and stateMachineSelectMove() functions after
	// doing the state-machine-related book-keeping.

	/**
	 * A wrapper function for stateMachineMetaGame. When the match begins, this
	 * initializes the state machine and role using the match description, and
	 * then calls stateMachineMetaGame.
	 */
	@Override
	public final void metaGame(long timeout) throws MetaGamingException
	{
		try
		{
			stateMachine = getInitialStateMachine();

			stateMachine.initialize(pruneRules(getMatch().getGame().getRules()));
			currentState = stateMachine.getInitialState();

			role = stateMachine.getRoleFromConstant(getRoleName());
			getMatch().appendState(currentState.getContents());

			stateMachineMetaGame(timeout);
		}
		catch (Exception e)
		{
		    GamerLogger.logStackTrace("GamePlayer", e);
			throw new MetaGamingException(e);
		}
	}

	/**
	 * A wrapper function for stateMachineSelectMove. When we are asked to
	 * select a move, this advances the state machine up to the current state
	 * and then calls stateMachineSelectMove to select a move based on that
	 * current state.
	 */
	@Override
	public final GdlTerm selectMove(long timeout) throws MoveSelectionException
	{
		try
		{
			stateMachine.doPerMoveWork();

			List<GdlTerm> lastMoves = getMatch().getMostRecentMoves();
			if (lastMoves != null)
			{
				List<Move> moves = new ArrayList<Move>();
				for (GdlTerm sentence : lastMoves)
				{
					moves.add(stateMachine.getMoveFromTerm(sentence));
				}

				currentState = stateMachine.getNextState(currentState, moves);
				getMatch().appendState(currentState.getContents());
			}

			return stateMachineSelectMove(timeout).getContents();
		}
		catch (Exception e)
		{
		    GamerLogger.logStackTrace("GamePlayer", e);
			throw new MoveSelectionException(e);
		}
	}

	@Override
	public void stop() throws StoppingException {
		try {
			stateMachine.doPerMoveWork();

			List<GdlTerm> lastMoves = getMatch().getMostRecentMoves();
			if (lastMoves != null)
			{
				List<Move> moves = new ArrayList<Move>();
				for (GdlTerm sentence : lastMoves)
				{
					moves.add(stateMachine.getMoveFromTerm(sentence));
				}

				currentState = stateMachine.getNextState(currentState, moves);
				getMatch().appendState(currentState.getContents());
				getMatch().markCompleted(stateMachine.getGoals(currentState));
			}

			stateMachineStop();
		}
		catch (Exception e)
		{
			GamerLogger.logStackTrace("GamePlayer", e);
			throw new StoppingException(e);
		}
	}

	@Override
	public void abort() throws AbortingException {
		try {
			stateMachineAbort();
		}
		catch (Exception e)
		{
			GamerLogger.logStackTrace("GamePlayer", e);
			throw new AbortingException(e);
		}
	}

    // Internal state about the current state of the state machine.
    private Role role;
    private MachineState currentState;
    private StateMachine stateMachine;
}