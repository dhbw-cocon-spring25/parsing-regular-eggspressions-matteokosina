package de.dhbw.mh.redeggs;



import java.util.Set;


/**
 * A parser for regular expressions using recursive descent parsing.
 * This class is responsible for converting a regular expression string into a
 * tree representation of a {@link RegularEggspression}.
 */
public class RecursiveDescentRedeggsParser {
	private String regexInput;
	private int initialLength = 0;
	private static final Set<Character> SPECIAL_LITERAL = Set.of('(', ')', '[', ']', '|', '*', '^', '\3');
	/**
	 * The symbol factory used to create symbols for the regular expression.
	 */
	protected final SymbolFactory symbolFactory;

	/**
	 * Constructs a new {@code RecursiveDescentRedeggsParser} with the specified
	 * symbol factory.
	 *
	 * @param symbolFactory the factory used to create symbols for parsing
	 */
	public RecursiveDescentRedeggsParser(SymbolFactory symbolFactory) {
		this.symbolFactory = symbolFactory;
	}

	/**
	 * Parses a regular expression string into an abstract syntax tree (AST).
	 * 
	 * This class uses recursive descent parsing to convert a given regular
	 * expression into a tree structure that can be processed or compiled further.
	 * The AST nodes represent different components of the regex such as literals,
	 * operators, and groups.
	 *
	 * @param regex the regular expression to parse
	 * @return the {@link RegularEggspression} representation of the parsed regex
	 * @throws RedeggsParseException if the parsing fails or the regex is invalid
	 */
	public RegularEggspression parse(String regex) throws RedeggsParseException {
		this.regexInput = regex;
		this.initialLength = regexInput.length();
		initialLength ++; // fix offset by test-suite

		if(regexInput.length() == 1) {
			switch (peek()) {
				case 'ε':
					return new RegularEggspression.EmptyWord();
				case '∅':
					return new RegularEggspression.EmptySet();
			}
		}
		RegularEggspression regularEggspression = parseRegex();
		if (this.peek() != '\3') {
			throw new RedeggsParseException("Unexpected symbol '" + this.peek() + "' at position " + (initialLength - regexInput.length()) + ".",initialLength - regexInput.length());
		}
		return regularEggspression;
	}

	// look at first character of regexInput without consuming
	private char peek() {
		if(regexInput.isEmpty()) {
			return '\3'; // EOF
		}
		return regexInput.charAt(0);
	}

	// look at first character of regexInput and consume it
	private char pop() {
		char c = peek();
		if(c != '\3') {
			regexInput = regexInput.substring(1);
		}
		return c;
	}

	private boolean isLiteral(char c) {
		return !SPECIAL_LITERAL.contains(c);
	}

	private RegularEggspression parseRegex() throws RedeggsParseException {
		// check select-set of initial-production in grammar
		char c = peek();
		if (c == '('|| c == '[' || isLiteral(c)) {
			// create production nodes
			RegularEggspression concat = concat();
			return union(concat);
		} else {
			throw new RedeggsParseException("Unexpected symbol '" + c + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
		}
	}

	private RegularEggspression union(RegularEggspression left) throws RedeggsParseException {

		if (peek() == '|') {
			pop(); // consume '|'
			RegularEggspression right = concat();
			return new RegularEggspression.Alternation(left, union(right));
		}else if (peek() == '\3' || peek() == ')') {
			return left;
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private RegularEggspression concat() throws RedeggsParseException {
		if (peek() == '(' || peek() == '[' || isLiteral(peek())) {
			RegularEggspression k = kleene();
			return suffix(k);
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private RegularEggspression suffix(RegularEggspression l) throws RedeggsParseException {
		if (isLiteral(peek()) || peek() == '(' || peek() == '[') {
			RegularEggspression k = kleene();
			return new RegularEggspression.Concatenation(l, suffix(k));
		} else if (peek() == '\3' || peek() == ')' || peek() == '|') {
			return l;
		}

		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private RegularEggspression kleene() throws RedeggsParseException {
		if (peek() == '(' || peek() == '[' || isLiteral(peek())) {
			RegularEggspression b = base();
			return star(b);
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private RegularEggspression star(RegularEggspression b) throws RedeggsParseException {
		if (peek() == '*') {
			pop(); // consume '*'
			return new RegularEggspression.Star(b);
		} else if (isLiteral(peek()) || peek() == '(' || peek() == '[' || peek() == '\3' || peek() == ')' || peek() == '|') {
			return b;
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private RegularEggspression base() throws RedeggsParseException {
		char select = peek();
		if (isLiteral(select)) {
			pop();
			VirtualSymbol v = symbolFactory.newSymbol().include(CodePointRange.single(select)).andNothingElse();
			return new RegularEggspression.Literal(v);
		} else if (select == '(') {
			pop();
			RegularEggspression r = parseRegex();
			if (pop() != ')') {
				throw new RedeggsParseException("Input ended unexpectedly, expected symbol ')' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
			}
			return r;
		} else if (select == '[') {
			pop();
			boolean neg = negation();
			SymbolFactory.Builder inhalt = inhalt(symbolFactory.newSymbol(), neg);
			SymbolFactory.Builder range = range(inhalt, neg);
			if(pop() != ']') {
				throw new RedeggsParseException("Input ended unexpectedly, expected symbol ']' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
			}

			return new RegularEggspression.Literal(range.andNothingElse());
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private boolean negation() throws RedeggsParseException {
		if (peek() == '^') {
			pop(); // consume '^'
			return true;
		} else if (isLiteral(peek())) {
			return false;
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private SymbolFactory.Builder range(SymbolFactory.Builder builder, boolean neg) throws RedeggsParseException {
		if (isLiteral(peek())) {
			SymbolFactory.Builder inhalt = inhalt(builder, neg);
			return range(inhalt, neg);
		} else if (peek() == ']') {
			return builder;
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private SymbolFactory.Builder inhalt(SymbolFactory.Builder builder, boolean neg) throws RedeggsParseException {
		char select = peek();
		if (isLiteral(select)) {
			pop(); // consume literal
			CodePointRange rest = rest(select);
			if (neg) {
				return builder.exclude(rest);
			} else {
				return builder.include(rest);
			}
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}

	private CodePointRange rest(char c) throws RedeggsParseException {
		char select = peek();
		if (select == '-') {
			pop();
			char consumed = pop();
			if(!isLiteral(consumed)){
				throw new RedeggsParseException(
						"Input ended unexpectedly, expected literal at position " + (initialLength - regexInput.length()) + ".",
						initialLength - regexInput.length());
			}
			return CodePointRange.range(c, consumed);
		} else if (isLiteral(peek()) || peek() == ']') {
			return CodePointRange.single(c);
		}
		throw new RedeggsParseException("Unexpected symbol '" + peek() + "' at position " + (initialLength - regexInput.length()) + ".", initialLength - regexInput.length());
	}
}
