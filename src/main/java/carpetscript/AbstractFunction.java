/*
 * Copyright 2018 Udo Klimaschewski
 * 
 * http://UdoJava.com/
 * http://about.me/udo.klimaschewski
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package carpetscript;

import java.util.ArrayList;
import java.util.List;

import carpetscript.Expression.LazyValue;

/**
 * Abstract implementation of a direct function.<br>
 * <br>
 * This abstract implementation does implement lazyEval so that it returns
 * the result of eval.
 */
public abstract class AbstractFunction extends AbstractLazyFunction implements IFunction {

	/**
	 * Creates a new function with given name and parameter count.
	 *
	 * @param name
	 *            The name of the function.
	 * @param numParams
	 *            The number of parameters for this function.
	 *            <code>-1</code> denotes a variable number of parameters.
	 */
	protected AbstractFunction(String name, int numParams) {
		super(name, numParams);
	}


	public LazyValue lazyEval(final List<LazyValue> lazyParams) {
		return new LazyValue() {

			private List<Value> params;

			public Value eval() {
				return AbstractFunction.this.eval(getParams());
			}

			private List<Value> getParams() {
				if (params == null) {
					params = new ArrayList<Value>();
					for (LazyValue lazyParam : lazyParams) {
						params.add(lazyParam.eval());
					}
				}
				return params;
			}
		};
	}
}
