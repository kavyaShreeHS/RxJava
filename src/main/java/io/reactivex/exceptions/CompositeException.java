/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.reactivex.exceptions;

import java.io.*;
import java.util.*;

/**
 * Represents an exception that is a composite of one or more other exceptions. A {@code CompositeException}
 * does not modify the structure of any exception it wraps, but at print-time it iterates through the list of
 * Throwables contained in the composite in order to print them all.
 *
 * Its invariant is to contain an immutable, ordered (by insertion order), unique list of non-composite
 * exceptions. You can retrieve individual exceptions in this list with {@link #getExceptions()}.
 * 
 * The {@link #printStackTrace()} implementation handles the StackTrace in a customized way instead of using
 * {@code getCause()} so that it can avoid circular references.
 * 
 * If you invoke {@link #getCause()}, it will lazily create the causal chain but will stop if it finds any
 * Throwable in the chain that it has already seen.
 */
public final class CompositeException extends RuntimeException {

    private static final long serialVersionUID = 3026362227162912146L;

    private final List<Throwable> exceptions;
    private final String message;
    private Throwable cause;

    public CompositeException() {
        this.exceptions = new ArrayList<Throwable>();
        this.message = null;
    }

    public CompositeException(Throwable... exceptions) {
        this.exceptions = new ArrayList<Throwable>();
        this.message = null;
        if (exceptions == null) {
            this.exceptions.add(new NullPointerException("exceptions is null"));
        } else {
            for (Throwable t : exceptions) {
                this.exceptions.add(t != null ? t : new NullPointerException("One of the exceptions is null"));
            }
        }
    }
    

    public CompositeException(Collection<? extends Throwable> errors) {
        Set<Throwable> deDupedExceptions = new LinkedHashSet<Throwable>();
        List<Throwable> localExceptions = new ArrayList<Throwable>();
        if (errors != null) {
            for (Throwable ex : errors) {
                if (ex instanceof CompositeException) {
                    deDupedExceptions.addAll(((CompositeException) ex).getExceptions());
                } else 
                if (ex != null) {
                    deDupedExceptions.add(ex);
                } else {
                    deDupedExceptions.add(new NullPointerException());
                }
            }
        } else {
            deDupedExceptions.add(new NullPointerException());
        }

        localExceptions.addAll(deDupedExceptions);
        this.exceptions = Collections.unmodifiableList(localExceptions);
        this.message = exceptions.size() + " exceptions occurred. ";
    }

    /**
     * Retrieves the list of exceptions that make up the {@code CompositeException}
     *
     * @return the exceptions that make up the {@code CompositeException}, as a {@link List} of {@link Throwable}s
     */
    public List<Throwable> getExceptions() {
        return exceptions;
    }

    @Override
    public String getMessage() {
        return message;
    }

    /**
     * Adds a suppressed exception to this composite.
     * <p>The method is named this way to avoid conflicts with Java 7 environments
     * and its addSuppressed() method.
     * @param e the exception to suppress, nulls are converted to NullPointerExceptions
     */
    public void suppress(Throwable e) {
        exceptions.add(e != null ? e : new NullPointerException("null exception"));
    }
    

    @Override
    public synchronized Throwable getCause() { // NOPMD
        if (cause == null) {
            // we lazily generate this causal chain if this is called
            CompositeExceptionCausalChain localCause = new CompositeExceptionCausalChain();
            Set<Throwable> seenCauses = new HashSet<Throwable>();

            Throwable chain = localCause;
            for (Throwable e : exceptions) {
                if (seenCauses.contains(e)) {
                    // already seen this outer Throwable so skip
                    continue;
                }
                seenCauses.add(e);
                
                List<Throwable> listOfCauses = getListOfCauses(e);
                // check if any of them have been seen before
                for(Throwable child : listOfCauses) {
                    if (seenCauses.contains(child)) {
                        // already seen this outer Throwable so skip
                        e = new RuntimeException("Duplicate found in causal chain so cropping to prevent loop ...");
                        continue;
                    }
                    seenCauses.add(child);
                }

                // we now have 'e' as the last in the chain
                try {
                    chain.initCause(e);
                } catch (Throwable t) {
                    // ignore
                    // the javadocs say that some Throwables (depending on how they're made) will never
                    // let me call initCause without blowing up even if it returns null
                }
                chain = chain.getCause();
            }
            cause = localCause;
        }
        return cause;
    }

    /**
     * All of the following {@code printStackTrace} functionality is derived from JDK {@link Throwable}
     * {@code printStackTrace}. In particular, the {@code PrintStreamOrWriter} abstraction is copied wholesale.
     *
     * Changes from the official JDK implementation:<ul>
     * <li>no infinite loop detection</li>
     * <li>smaller critical section holding {@link PrintStream} lock</li>
     * <li>explicit knowledge about the exceptions {@link List} that this loops through</li>
     * </ul>
     */
    @Override
    public void printStackTrace() {
        printStackTrace(System.err);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        printStackTrace(new WrappedPrintStream(s));
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        printStackTrace(new WrappedPrintWriter(s));
    }

    /**
     * Special handling for printing out a {@code CompositeException}.
     * Loops through all inner exceptions and prints them out.
     * 
     * @param s
     *            stream to print to
     */
    private void printStackTrace(PrintStreamOrWriter s) {
        StringBuilder bldr = new StringBuilder(128);
        bldr.append(this).append('\n');
        for (StackTraceElement myStackElement : getStackTrace()) {
            bldr.append("\tat ").append(myStackElement).append('\n');
        }
        int i = 1;
        for (Throwable ex : exceptions) {
            bldr.append("  ComposedException ").append(i).append(" :\n");
            appendStackTrace(bldr, ex, "\t");
            i++;
        }
        synchronized (s.lock()) {
            s.println(bldr.toString());
        }
    }

    private void appendStackTrace(StringBuilder bldr, Throwable ex, String prefix) {
        bldr.append(prefix).append(ex).append('\n');
        for (StackTraceElement stackElement : ex.getStackTrace()) {
            bldr.append("\t\tat ").append(stackElement).append('\n');
        }
        if (ex.getCause() != null) {
            bldr.append("\tCaused by: ");
            appendStackTrace(bldr, ex.getCause(), "");
        }
    }

    abstract static class PrintStreamOrWriter {
        /** Returns the object to be locked when using this StreamOrWriter */
        abstract Object lock();

        /** Prints the specified string as a line on this StreamOrWriter */
        abstract void println(Object o);
    }

    /**
     * Same abstraction and implementation as in JDK to allow PrintStream and PrintWriter to share implementation
     */
    static final class WrappedPrintStream extends PrintStreamOrWriter {
        private final PrintStream printStream;

        WrappedPrintStream(PrintStream printStream) {
            this.printStream = printStream;
        }

        @Override
        Object lock() {
            return printStream;
        }

        @Override
        void println(Object o) {
            printStream.println(o);
        }
    }

    static final class WrappedPrintWriter extends PrintStreamOrWriter {
        private final PrintWriter printWriter;

        WrappedPrintWriter(PrintWriter printWriter) {
            this.printWriter = printWriter;
        }

        @Override
        Object lock() {
            return printWriter;
        }

        @Override
        void println(Object o) {
            printWriter.println(o);
        }
    }

    final static class CompositeExceptionCausalChain extends RuntimeException {
        private static final long serialVersionUID = 3875212506787802066L;
        /* package-private */static final String MESSAGE = "Chain of Causes for CompositeException In Order Received =>";

        @Override
        public String getMessage() {
            return MESSAGE;
        }
    }

    private List<Throwable> getListOfCauses(Throwable ex) {
        List<Throwable> list = new ArrayList<Throwable>();
        Throwable root = ex.getCause();
        if (root == null) {
            return list;
        } else {
            while(true) {
                list.add(root);
                if (root.getCause() == null) {
                    return list;
                } else {
                    root = root.getCause();
                }
            }
        }
    }
    public int size() {
        return exceptions.size();
    }

    /**
     * Returns true if this CompositeException doesn't have a cause or
     * any suppressed exceptions.
     * @return true if this CompositeException doesn't have a cause or
     * any suppressed exceptions.
     */
    public boolean isEmpty() {
        return exceptions.isEmpty() && getCause() == null;
    }
}