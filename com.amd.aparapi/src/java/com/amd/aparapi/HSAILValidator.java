package com.amd.aparapi;


import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HSAILValidator {
    static enum State {NONE, KERNEL_ARGS, BODY, LABEL, MULTILINE_COMMENT}

    ;

    static class Instruction {
        int lineNumber;
        Label label;
        String content;
        String tailComment;
        String mnemonic;
        String[] operands;
        boolean special = false;
        static Pattern tailCommentPattern = Pattern.compile("^ *(.*) *; *//(.*)");
        static Pattern noTailCommentPattern = Pattern.compile("^ *(.*) *; *");

        Instruction(int _lineNumber, String _content, String _tailComment, Label _label) {
            lineNumber = _lineNumber;
            content = _content;
            if (content.contains(",")){
                int firstSpace = content.indexOf(' ');
                mnemonic = content.substring(0,firstSpace);
                operands = content.substring(firstSpace).split(",");
            }             else{
                mnemonic = content;
                operands = new String[0];
            }
            tailComment = _tailComment;
            label = _label;

        }
        static Instruction create(int _lineNumber, String _content) {
            return(create(_lineNumber, _content, null));
        }
        static Instruction create(int _lineNumber, String _content, Label _label) {
            String content=null;
            String tailComment = null;
            Matcher matcher = tailCommentPattern.matcher(_content);
            if (matcher.matches()) {
                content = matcher.group(1);
                tailComment = matcher.group(2);
            } else {
                matcher = noTailCommentPattern.matcher(_content);
                if (matcher.matches()) {
                    content = matcher.group(1);
                    tailComment = null;

                } else {
                   throw new IllegalStateException("what?");
                }

            }
            return new Instruction(_lineNumber, content, tailComment, _label);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (label != null) {
                sb.append(label.name).append(":\n");
            }
            sb.append("   " + content + "// {"+mnemonic+"} ");
            return (sb.toString());
        }
    }

    static class Label {
        String name;

        Label(String _name) {
            name = _name;
        }
    }

    static String labelRegexCapture = "(\\@L[a-zA-Z0-9_]+)";

    static class LineMatcher {
        Pattern pattern;
        Matcher matcher;

        String getGroup(int group) {
            return (matcher.group(group));
        }

        boolean matches(String line) {
            Matcher lineMatcher = pattern.matcher(line);
            if (lineMatcher.matches()) {
                matcher = lineMatcher;

            } else {
                matcher = null;

            }
            return (matcher != null);
        }

        LineMatcher(Pattern _pattern) {
            pattern = _pattern;
        }
    }

    static LineMatcher labelMatcher = new LineMatcher(Pattern.compile("^ *" + labelRegexCapture + ": *"));
    static LineMatcher whiteSpaceMatcher = new LineMatcher(Pattern.compile("^ *//(.*)"));
    static LineMatcher multiLineStartMatcher = new LineMatcher(Pattern.compile("^ */\\*(.*)"));
    static LineMatcher multiLineEndMatcher = new LineMatcher(Pattern.compile("^ *\\*/(.*)"));
    static LineMatcher versionMatcher = new LineMatcher(Pattern.compile("^ *version *([0-9]+:[0-9]+:) *(\\$[a-z]+) *: *(\\$[a-z]+).*"));
    static LineMatcher kernelMatcher = new LineMatcher(Pattern.compile("^ *kernel.*"));

    static LineMatcher kernelArgMatcher = new LineMatcher(Pattern.compile("^ *kernarg_([usb](64|32|16|8)) *(\\%_arg[0-9]+).*"));
    static LineMatcher bodyStartMatcher = new LineMatcher(Pattern.compile("^ *\\)\\{ *"));
    static LineMatcher bodyEndMatcher = new LineMatcher(Pattern.compile("^ *\\}; *"));

    public static void main(String[] _args) throws IOException {
        String fileName = "C:\\Users\\user1\\aparapi\\branches\\lambda\\sindexof.hsail";
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));

        List<String> input = new ArrayList<String>();
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            input.add(line);
        }
        br.close();
        Label label = null;
        int lineNumber = 0;
        Stack<State> state = new Stack<State>();
        state.push(State.NONE);
        List<Instruction> instructions = new ArrayList<Instruction>();
        for (String line : input) {
            if (line.trim().equals("")) {
                // skip
            } else if (whiteSpaceMatcher.matches(line)) {
                // skip
            } else {
                switch (state.peek()) {
                    case MULTILINE_COMMENT:
                        if (multiLineEndMatcher.matches(line)) {
                            state.pop();
                        } else {
                            // skip
                        }
                        break;
                    case NONE:
                        if (versionMatcher.matches(line)) {
                            // System.out.println("version " + versionMatcher.getGroup(1) + " " + versionMatcher.getGroup(2) + " " + versionMatcher.getGroup(3));
                        } else if (kernelMatcher.matches(line)) {
                            // System.out.println("kernel " + kernelMatcher.getGroup(0));
                            state.pop(); // replace PREAMBLE with ARGS
                            state.push(State.KERNEL_ARGS);
                        } else if (multiLineStartMatcher.matches(line)) {
                            state.push(State.MULTILINE_COMMENT);
                        } else {
                            throw new IllegalStateException("what is this doing here!");
                        }
                        break;
                    case KERNEL_ARGS:
                        if (kernelArgMatcher.matches(line)) {
                            //System.out.println("kernarg " + kernelArgMatcher.getGroup(1) + " " + kernelArgMatcher.getGroup(3));
                        } else if (bodyStartMatcher.matches(line)) {
                            state.pop(); // replace ARGS with BODY!
                            state.push(State.BODY);
                        } else if (multiLineStartMatcher.matches(line)) {
                            state.push(State.MULTILINE_COMMENT);
                        } else {
                            throw new IllegalStateException("what is this doing here!");
                        }
                        break;
                    case BODY:
                        if (bodyEndMatcher.matches(line)) {
                            state.pop();
                            state.push(State.NONE);
                        } else if (multiLineStartMatcher.matches(line)) {
                            state.push(State.MULTILINE_COMMENT);
                        } else if (labelMatcher.matches(line)) {
                            label = new Label(labelMatcher.getGroup(1));
                            state.push(State.LABEL);
                        } else {
                            instructions.add(Instruction.create( lineNumber, line));
                        }
                        break;
                    case LABEL:
                        if (multiLineStartMatcher.matches(line)) {
                            state.push(State.MULTILINE_COMMENT);
                        } else {
                            instructions.add(Instruction.create(lineNumber, line, label));
                            state.pop();
                        }
                        break;

                }
            }
            lineNumber++;
        }
        for (Instruction i : instructions) {
            System.out.println(i);
        }


    }

}
