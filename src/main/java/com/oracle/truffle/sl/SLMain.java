/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 * 
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 * 
 * (a) the Software, and
 * 
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 * 
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 * 
 * This license is subject to the following condition:
 * 
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl;

import java.io.*;
import java.math.*;
import java.util.Arrays;
import java.util.Scanner;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.tools.*;
import com.oracle.truffle.sl.builtins.*;
import com.oracle.truffle.sl.factory.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.call.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.expression.*;
import com.oracle.truffle.sl.nodes.instrument.*;
import com.oracle.truffle.sl.nodes.local.*;
import com.oracle.truffle.sl.parser.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * SL is a simple language to demonstrate and showcase features of Truffle. The implementation is as
 * simple and clean as possible in order to help understanding the ideas and concepts of Truffle.
 * The language has first class functions, but no object model.
 * <p>
 * SL is dynamically typed, i.e., there are no type names specified by the programmer. SL is
 * strongly typed, i.e., there is no automatic conversion between types. If an operation is not
 * available for the types encountered at run time, a type error is reported and execution is
 * stopped. For example, {@code 4 - "2"} results in a type error because subtraction is only defined
 * for numbers.
 *
 * <p>
 * <b>Types:</b>
 * <ul>
 * <li>Number: arbitrary precision integer numbers. The implementation uses the Java primitive type
 * {@code long} to represent numbers that fit into the 64 bit range, and {@link BigInteger} for
 * numbers that exceed the range. Using a primitive type such as {@code long} is crucial for
 * performance.
 * <li>Boolean: implemented as the Java primitive type {@code boolean}.
 * <li>String: implemented as the Java standard type {@link String}.
 * <li>Function: implementation type {@link SLFunction}.
 * <li>Null (with only one value {@code null}): implemented as the singleton
 * {@link SLNull#SINGLETON}.
 * </ul>
 * The class {@link SLTypes} lists these types for the Truffle DSL, i.e., for type-specialized
 * operations that are specified using Truffle DSL annotations.
 *
 * <p>
 * <b>Language concepts:</b>
 * <ul>
 * <li>Literals for {@link SLBigIntegerLiteralNode numbers} , {@link SLStringLiteralNode strings},
 * and {@link SLFunctionLiteralNode functions}.
 * <li>Basic arithmetic, logical, and comparison operations: {@link SLAddNode +}, {@link SLSubNode
 * -}, {@link SLMulNode *}, {@link SLDivNode /}, {@link SLLogicalAndNode logical and},
 * {@link SLLogicalOrNode logical or}, {@link SLEqualNode ==}, !=, {@link SLLessThanNode &lt;},
 * {@link SLLessOrEqualNode &le;}, &gt;, &ge;.
 * <li>Local variables: local variables must be defined (via a {@link SLWriteLocalVariableNode
 * write}) before they can be used (by a {@link SLReadLocalVariableNode read}). Local variables are
 * not visible outside of the block where they were first defined.
 * <li>Basic control flow statements: {@link SLBlockNode blocks}, {@link SLIfNode if},
 * {@link SLWhileNode while} with {@link SLBreakNode break} and {@link SLContinueNode continue},
 * {@link SLReturnNode return}.
 * <li>Function calls: {@link SLInvokeNode invocations} are efficiently implemented with
 * {@link SLDispatchNode polymorphic inline caches}.
 * </ul>
 *
 * <p>
 * <b>Syntax and parsing:</b><br>
 * The syntax is described as an attributed grammar. The {@link Parser} and {@link Scanner} are
 * automatically generated by the parser generator Coco/R (available from <a
 * href="http://ssw.jku.at/coco/">http://ssw.jku.at/coco/</a>). The grammar contains semantic
 * actions that build the AST for a method. To keep these semantic actions short, they are mostly
 * calls to the {@link SLNodeFactory} that performs the actual node creation. All functions found in
 * the SL source are added to the {@link SLFunctionRegistry}, which is accessible from the
 * {@link SLContext}.
 *
 * <p>
 * <b>Builtin functions:</b><br>
 * Library functions that are available to every SL source without prior definition are called
 * builtin functions. They are added to the {@link SLFunctionRegistry} when the {@link SLContext} is
 * created. There current builtin functions are
 * <ul>
 * <li>{@link SLReadlnBuiltin readln}: Read a String from the {@link SLContext#getInput() standard
 * input}.
 * <li>{@link SLPrintlnBuiltin println}: Write a value to the {@link SLContext#getOutput() standard
 * output}.
 * <li>{@link SLNanoTimeBuiltin nanoTime}: Returns the value of a high-resolution time, in
 * nanoseconds.
 * <li>{@link SLDefineFunctionBuiltin defineFunction}: Parses the functions provided as a String
 * argument and adds them to the function registry. Functions that are already defined are replaced
 * with the new version.
 * </ul>
 *
 * <p>
 * <b>Tools:</b><br>
 * The use of some of Truffle's support for developer tools (based on the Truffle Instrumentation
 * Framework) are demonstrated in this file, for example:
 * <ul>
 * <li>a {@linkplain NodeExecCounter counter for node executions}, tabulated by node type; and</li>
 * <li>a simple {@linkplain CoverageTracker code coverage engine}.</li>
 * </ul>
 * In each case, the tool is enabled if a corresponding local boolean variable in this file is set
 * to {@code true}. Results are printed at the end of the execution using each tool's
 * <em>default printer</em>.
 *
 */
public class SLMain {

    /* Demonstrate per-type tabulation of node execution counts */
    private static boolean nodeExecCounts = false;
    /* Demonstrate per-line tabulation of STATEMENT node execution counts */
    private static boolean statementCounts = false;
    /* Demonstrate per-line tabulation of STATEMENT coverage */
    private static boolean coverage = false;

    /**
     * The main entry point. Use the mx command "mx sl" to run it with the correct class path setup.
     */
    public static void main(String[] args) throws IOException {

        SLContext context = SLContextFactory.create(new BufferedReader(new InputStreamReader(System.in)), System.out);

        Source source;
        if (args.length == 0) {
            source = Source.fromReader(new InputStreamReader(System.in), "stdin");
        } else {
            source = Source.fromFileName(args[0]);
        }

        int repeats = 1;
        if (args.length >= 2) {
            repeats = Integer.parseInt(args[1]);
        }

        run(context, source, System.out, repeats);
    }

    /**
     * Parse and run the specified SL source. Factored out in a separate method so that it can also
     * be used by the unit test harness.
     */
    public static long run(SLContext context, Source source, PrintStream logOutput, int repeats) {
        if (logOutput != null) {
            logOutput.println("== running on " + Truffle.getRuntime().getName());
            // logOutput.println("Source = " + source.getCode());
        }

        if (statementCounts || coverage) {
            Probe.registerASTProber(new SLStandardASTProber());
        }

        NodeExecCounter nodeExecCounter = null;
        if (nodeExecCounts) {
            nodeExecCounter = new NodeExecCounter();
            nodeExecCounter.install();
        }

        NodeExecCounter statementExecCounter = null;
        if (statementCounts) {
            statementExecCounter = new NodeExecCounter(StandardSyntaxTag.STATEMENT);
            statementExecCounter.install();
        }

        CoverageTracker coverageTracker = null;
        if (coverage) {
            coverageTracker = new CoverageTracker();
            coverageTracker.install();
        }

        /* Parse the SL source file. */
        Parser.parseSL(context, source);

        /* Lookup our main entry point, which is per definition always named "main". */
        SLFunction main = context.getFunctionRegistry().lookup("main");
        if (main.getCallTarget() == null) {
            throw new SLException("No function main() defined in SL source file.");
        }

        /* Change to true if you want to see the AST on the console. */
        boolean printASTToLog = false;
        /* Change to true if you want to see source attribution for the AST to the console */
        boolean printSourceAttributionToLog = false;
        /* Change to dump the AST to IGV over the network. */
        boolean dumpASTToIGV = false;

        printScript("before execution", context, logOutput, printASTToLog, printSourceAttributionToLog, dumpASTToIGV);
        long totalRuntime = 0;
        try {
            for (int i = 0; i < repeats; i++) {
                long start = System.nanoTime();
                /* Call the main entry point, without any arguments. */
                try {
                    Object result = main.getCallTarget().call();
                    if (result != SLNull.SINGLETON) {
                    	SLFunction function = context.getFunctionRegistry().lookup("println");
                        
                    	if (function != null) {
                    		function.getCallTarget().call(result);
                    	} else {
                    		context.getOutput().println(result);
                    	}
                    }
                } catch (UnsupportedSpecializationException ex) {
                    context.getOutput().println(formatTypeError(ex));
                }
                long end = System.nanoTime();
                totalRuntime += end - start;

                if (logOutput != null && repeats > 1) {
                    logOutput.println("== iteration " + (i + 1) + ": " + ((end - start) / 1000000) + " ms");
                }
            }

        } finally {
            printScript("after execution", context, logOutput, printASTToLog, printSourceAttributionToLog, dumpASTToIGV);
        }
        if (nodeExecCounter != null) {
            nodeExecCounter.print(System.out);
            nodeExecCounter.dispose();
        }
        if (statementExecCounter != null) {
            statementExecCounter.print(System.out);
            statementExecCounter.dispose();
        }
        if (coverageTracker != null) {
            coverageTracker.print(System.out);
            coverageTracker.dispose();
        }
        return totalRuntime;
    }

    /**
     * When dumpASTToIGV is true: dumps the AST of all functions to the IGV visualizer, via a socket
     * connection. IGV can be started with the mx command "mx igv".
     * <p>
     * When printASTToLog is true: prints the ASTs to the console.
     */
    private static void printScript(String groupName, SLContext context, PrintStream logOutput, boolean printASTToLog, boolean printSourceAttributionToLog, boolean dumpASTToIGV) {
        if (dumpASTToIGV) {
            GraphPrintVisitor graphPrinter = new GraphPrintVisitor();
            graphPrinter.beginGroup(groupName);
            for (SLFunction function : context.getFunctionRegistry().getFunctions()) {
                RootCallTarget callTarget = function.getCallTarget();
                if (callTarget != null) {
                    graphPrinter.beginGraph(function.toString()).visit(callTarget.getRootNode());
                }
            }
            graphPrinter.printToNetwork(true);
        }
        if (printASTToLog && logOutput != null) {
            for (SLFunction function : context.getFunctionRegistry().getFunctions()) {
                RootCallTarget callTarget = function.getCallTarget();
                if (callTarget != null) {
                    logOutput.println("=== " + function);
                    NodeUtil.printTree(logOutput, callTarget.getRootNode());
                }
            }
        }
        if (printSourceAttributionToLog && logOutput != null) {
            for (SLFunction function : context.getFunctionRegistry().getFunctions()) {
                RootCallTarget callTarget = function.getCallTarget();
                if (callTarget != null) {
                    logOutput.println("=== " + function);
                    NodeUtil.printSourceAttributionTree(logOutput, callTarget.getRootNode());
                }
            }
        }
    }

    /**
     * Provides a user-readable message for run-time type errors. SL is strongly typed, i.e., there
     * are no automatic type conversions of values. Therefore, Truffle does the type checking for
     * us: if no matching node specialization for the actual values is found, then we have a type
     * error. Specialized nodes use the {@link UnsupportedSpecializationException} to report that no
     * specialization was found. We therefore just have to convert the information encapsulated in
     * this exception in a user-readable form.
     */
    private static String formatTypeError(UnsupportedSpecializationException ex) {
        StringBuilder result = new StringBuilder();
        result.append("Type error");
        if (ex.getNode() != null && ex.getNode().getSourceSection() != null) {
            SourceSection ss = ex.getNode().getSourceSection();
            if (ss != null && !(ss instanceof NullSourceSection)) {
                result.append(" at ").append(ss.getSource().getName()).append(" line ").append(ss.getStartLine()).append(" col ").append(ss.getStartColumn());
            }
        }
        result.append(": operation");
        if (ex.getNode() != null) {
            NodeInfo nodeInfo = SLContext.lookupNodeInfo(ex.getNode().getClass());
            if (nodeInfo != null) {
                result.append(" \"").append(nodeInfo.shortName()).append("\"");
            }
        }
        result.append(" not defined for");

        String sep = " ";
        for (int i = 0; i < ex.getSuppliedValues().length; i++) {
            Object value = ex.getSuppliedValues()[i];
            Node node = ex.getSuppliedNodes()[i];
            if (node != null) {
                result.append(sep);
                sep = ", ";

                if (value instanceof Long || value instanceof BigInteger) {
                    result.append("Number ").append(value);
                } else if (value instanceof Boolean) {
                    result.append("Boolean ").append(value);
                } else if (value instanceof String) {
                    result.append("String \"").append(value).append("\"");
                } else if (value instanceof SLFunction) {
                    result.append("Function ").append(value);
                } else if (value == SLNull.SINGLETON) {
                    result.append("NULL");
                } else if (value == null) {
                    // value is not evaluated because of short circuit evaluation
                    result.append("ANY");
                } else {
                    result.append(value);
                }
            }
        }
        return result.toString();
    }

}
