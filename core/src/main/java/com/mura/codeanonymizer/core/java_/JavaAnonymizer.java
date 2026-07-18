package com.mura.codeanonymizer.core.java_;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;

import com.mura.codeanonymizer.core.AnonymizeOptions;
import com.mura.codeanonymizer.core.AnonymizeResult;
import com.mura.codeanonymizer.core.mapping.MappingStore;
import com.mura.codeanonymizer.core.mapping.NameKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class JavaAnonymizer {

    private final MappingStore store;

    public JavaAnonymizer(MappingStore store) {
        this.store = store;
    }

    /** コード断片をラップして解析した場合の種別。出力時にラッパーを剥がすために使う。 */
    private enum FragmentKind { COMPILATION_UNIT, MEMBERS, STATEMENTS }

    private static final String WRAPPER_CLASS = "CodeAnonymizerFragmentWrapper";
    private static final String WRAPPER_METHOD = "codeAnonymizerFragmentMethod";

    public AnonymizeResult anonymize(String source, AnonymizeOptions options) {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        StaticJavaParser.setConfiguration(config);

        CompilationUnit cu;
        FragmentKind fragmentKind;
        try {
            cu = StaticJavaParser.parse(source);
            fragmentKind = FragmentKind.COMPILATION_UNIT;
        } catch (ParseProblemException originalError) {
            // メソッド単体やフィールドだけの断片は、合成クラスでラップすると解析できる
            try {
                cu = StaticJavaParser.parse("class " + WRAPPER_CLASS + " {\n" + source + "\n}");
                fragmentKind = FragmentKind.MEMBERS;
            } catch (ParseProblemException ignored) {
                // 文だけの断片は、さらに合成メソッドでラップして解析する
                try {
                    cu = StaticJavaParser.parse(
                            "class " + WRAPPER_CLASS + " { void " + WRAPPER_METHOD + "() {\n" + source + "\n} }");
                    fragmentKind = FragmentKind.STATEMENTS;
                } catch (ParseProblemException ignored2) {
                    // getMessage()は全問題のスタックトレースを含む巨大な文字列になるため、
                    // 最初の失敗(元の入力そのまま)の先頭1行だけをユーザー向けメッセージに使う
                    throw new JavaAnonymizeException(
                            "Javaソースとして解析できませんでした: " + firstProblemLine(originalError)
                            + "\n入力がJavaコードか、モード選択が正しいか確認してください(XML・プロパティファイル等は非対応です)。",
                            originalError);
                }
            }
        } catch (RuntimeException e) {
            throw new JavaAnonymizeException("Javaソースの解析に失敗しました: " + e.getClass().getSimpleName(), e);
        }

        List<String> warnings = new ArrayList<>();

        Map<String, NameKind> declared = collectDeclarations(cu);
        Map<String, String> renameMap = new LinkedHashMap<>();
        for (Map.Entry<String, NameKind> entry : declared.entrySet()) {
            renameMap.put(entry.getKey(), store.getOrCreate(entry.getKey(), entry.getValue()));
        }

        // import文を先に処理し、importで導入されたクラス名のリネームをrenameMapへ合流させる
        // (本文中の使用箇所と一貫させるため、renameSimpleNamesより前に行う必要がある)
        Set<String> libraryImportedNames = renameImports(cu, renameMap);
        detectUnresolvedTypeWarnings(cu, renameMap, libraryImportedNames, warnings);
        renameSimpleNames(cu, renameMap);
        renameMethodReferences(cu, renameMap);
        renamePackageDeclaration(cu);

        if (options.isMaskStrings()) {
            maskStringLiterals(cu);
        } else {
            warnStringLiteralsNotMasked(cu, warnings);
        }

        detectAnnotationStringWarnings(cu, warnings);

        if (options.isRemoveComments()) {
            removeComments(cu);
        }

        return new AnonymizeResult(render(cu, fragmentKind), warnings);
    }

    /** ラップして解析した場合は、合成したクラス/メソッドを剥がして断片部分だけを文字列化する。 */
    private static String render(CompilationUnit cu, FragmentKind kind) {
        if (kind == FragmentKind.COMPILATION_UNIT) {
            return cu.toString();
        }
        ClassOrInterfaceDeclaration wrapper = cu.getClassByName(WRAPPER_CLASS)
                .orElseThrow(() -> new IllegalStateException("ラッパークラスが見つかりません"));
        if (kind == FragmentKind.MEMBERS) {
            StringBuilder sb = new StringBuilder();
            wrapper.getMembers().forEach(m -> {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(m.toString());
            });
            return sb.toString();
        }
        MethodDeclaration method = wrapper.getMethodsByName(WRAPPER_METHOD).get(0);
        StringBuilder sb = new StringBuilder();
        method.getBody().ifPresent(body -> body.getStatements().forEach(st -> {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(st.toString());
        }));
        return sb.toString();
    }

    /** ParseProblemExceptionから先頭の問題の1行目だけを取り出し、最大200文字に丸める。 */
    private static String firstProblemLine(ParseProblemException e) {
        String message = e.getProblems().isEmpty()
                ? String.valueOf(e.getMessage())
                : e.getProblems().get(0).getMessage();
        if (message == null) {
            return "(詳細不明)";
        }
        int newline = message.indexOf('\n');
        if (newline > 0) {
            message = message.substring(0, newline);
        }
        if (message.length() > 200) {
            message = message.substring(0, 200) + "…";
        }
        return message.trim();
    }

    private Map<String, NameKind> collectDeclarations(CompilationUnit cu) {
        Map<String, NameKind> declared = new LinkedHashMap<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(d ->
                addIfEligible(declared, d.getNameAsString(), NameKind.CLASS));
        cu.findAll(EnumDeclaration.class).forEach(d ->
                addIfEligible(declared, d.getNameAsString(), NameKind.CLASS));
        cu.findAll(RecordDeclaration.class).forEach(d ->
                addIfEligible(declared, d.getNameAsString(), NameKind.CLASS));
        cu.findAll(MethodDeclaration.class).forEach(d ->
                addIfEligible(declared, d.getNameAsString(), NameKind.METHOD));
        cu.findAll(FieldDeclaration.class).forEach(d ->
                d.getVariables().forEach(v -> addIfEligible(declared, v.getNameAsString(), NameKind.VARIABLE)));
        cu.findAll(VariableDeclarator.class).forEach(v ->
                addIfEligible(declared, v.getNameAsString(), NameKind.VARIABLE));
        cu.findAll(Parameter.class).forEach(p ->
                addIfEligible(declared, p.getNameAsString(), NameKind.VARIABLE));
        cu.findAll(EnumConstantDeclaration.class).forEach(c ->
                addIfEligible(declared, c.getNameAsString(), NameKind.VARIABLE));
        cu.findAll(AnnotationDeclaration.class).forEach(d ->
                addIfEligible(declared, d.getNameAsString(), NameKind.CLASS));
        cu.findAll(AnnotationMemberDeclaration.class).forEach(m ->
                addIfEligible(declared, m.getNameAsString(), NameKind.METHOD));

        return declared;
    }

    private void addIfEligible(Map<String, NameKind> declared, String name, NameKind kind) {
        if (JavaDenylist.contains(name)) {
            return;
        }
        // 断片解析用にこちらで合成した名前はリネーム対象にしない
        if (WRAPPER_CLASS.equals(name) || WRAPPER_METHOD.equals(name)) {
            return;
        }
        declared.putIfAbsent(name, kind);
    }

    private void renameSimpleNames(CompilationUnit cu, Map<String, String> renameMap) {
        if (renameMap.isEmpty()) {
            return;
        }
        cu.walk(SimpleName.class, sn -> {
            String replacement = renameMap.get(sn.getIdentifier());
            if (replacement != null) {
                sn.setIdentifier(replacement);
            }
        });
    }

    /**
     * メソッド参照(Foo::bar)の右辺はSimpleNameではなく文字列として保持されるため、
     * walk(SimpleName)では置換されない。ここで個別にリネームする。
     */
    private void renameMethodReferences(CompilationUnit cu, Map<String, String> renameMap) {
        cu.findAll(MethodReferenceExpr.class).forEach(ref -> {
            String replacement = renameMap.get(ref.getIdentifier());
            if (replacement != null) {
                ref.setIdentifier(replacement);
            }
        });
    }

    /**
     * 宣言もimportもされていない型(同一パッケージの社内クラスを暗黙参照している可能性が高い)を
     * 検出して警告する。名前ベースの解決では安全にリネームできないため、警告に留める。
     */
    private void detectUnresolvedTypeWarnings(CompilationUnit cu, Map<String, String> renameMap,
                                              Set<String> libraryImportedNames, List<String> warnings) {
        Set<String> typeParams = new HashSet<>();
        cu.findAll(TypeParameter.class).forEach(tp -> typeParams.add(tp.getNameAsString()));

        Set<String> unresolved = new TreeSet<>();
        cu.findAll(ClassOrInterfaceType.class).forEach(t -> {
            if (t.getScope().isPresent()) {
                return;
            }
            // FQCN(java.util.function.Supplier等)のパッケージセグメントは、
            // 親がClassOrInterfaceTypeであるスコープ修飾子として現れるため除外する
            if (t.getParentNode().filter(p -> p instanceof ClassOrInterfaceType).isPresent()) {
                return;
            }
            String name = t.getNameAsString();
            if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) {
                return;
            }
            if (renameMap.containsKey(name) || JavaDenylist.contains(name)
                    || libraryImportedNames.contains(name) || typeParams.contains(name)) {
                return;
            }
            unresolved.add(name);
        });
        if (!unresolved.isEmpty()) {
            warnings.add("宣言もimportもされていない型を検出しました(同一パッケージの社内クラスの可能性があり、"
                    + "匿名化されません): " + String.join(", ", unresolved));
        }

        detectUnresolvedMethodCallWarnings(cu, unresolved, warnings);
    }

    /**
     * 未解決の型(同一パッケージの社内クラスの可能性がある型)の変数に対するメソッド呼び出しを検出する。
     * 例: TestBean bean = ...; bean.getOriginName() の getOriginName() のような、
     * getter/setter名に業務用語が含まれるケースがそのまま漏れるため、リネームできない旨を警告する。
     */
    private void detectUnresolvedMethodCallWarnings(CompilationUnit cu, Set<String> unresolvedTypes,
                                                     List<String> warnings) {
        if (unresolvedTypes.isEmpty()) {
            return;
        }

        Map<String, String> varToType = new HashMap<>();
        cu.findAll(VariableDeclarator.class).forEach(v -> {
            if (v.getType() instanceof ClassOrInterfaceType type) {
                varToType.put(v.getNameAsString(), type.getNameAsString());
            }
        });
        cu.findAll(Parameter.class).forEach(p -> {
            if (p.getType() instanceof ClassOrInterfaceType type) {
                varToType.put(p.getNameAsString(), type.getNameAsString());
            }
        });

        Set<String> calls = new TreeSet<>();
        cu.findAll(MethodCallExpr.class).forEach(call -> {
            call.getScope().filter(s -> s instanceof NameExpr).ifPresent(scope -> {
                String varName = ((NameExpr) scope).getNameAsString();
                String typeName = varToType.get(varName);
                if (typeName != null && unresolvedTypes.contains(typeName)) {
                    calls.add(varName + "." + call.getNameAsString() + "(...)");
                }
            });
        });

        if (!calls.isEmpty()) {
            warnings.add("未解決の型に対するメソッド呼び出しを検出しました(getter/setter名等が匿名化されずに残ります): "
                    + String.join(", ", calls));
        }
    }

    private void warnStringLiteralsNotMasked(CompilationUnit cu, List<String> warnings) {
        int count = cu.findAll(StringLiteralExpr.class).size()
                + cu.findAll(TextBlockLiteralExpr.class).size();
        if (count > 0) {
            warnings.add("文字列リテラルが" + count + "件含まれています(URL、SQL、業務メッセージ等が漏れる可能性)。"
                    + "必要なら「文字列リテラルをマスク」を有効にしてください。");
        }
    }

    private void renamePackageDeclaration(CompilationUnit cu) {
        cu.getPackageDeclaration().ifPresent((PackageDeclaration pkg) -> {
            String oldPkg = pkg.getNameAsString();
            String newPkg = store.getOrCreate(oldPkg, NameKind.PACKAGE);
            pkg.setName(newPkg);
        });
    }

    /**
     * 既知ライブラリ以外のimportは社内コードとみなし、パッケージ部・クラス部・
     * (staticインポートの)メンバ部をそれぞれ匿名化する。
     * リネームしたクラス名/メンバ名はrenameMapに合流させ、本文中の使用箇所と一貫させる。
     */
    private Set<String> renameImports(CompilationUnit cu, Map<String, String> renameMap) {
        Set<String> libraryImportedNames = new HashSet<>();
        for (ImportDeclaration imp : cu.getImports()) {
            String name = imp.getNameAsString();
            if (JavaDenylist.isLibraryPackage(name)) {
                if (!imp.isAsterisk()) {
                    libraryImportedNames.add(tailAfterLastDot(name));
                    if (imp.isStatic()) {
                        String classPath = headBeforeLastDot(name);
                        if (classPath != null) {
                            libraryImportedNames.add(tailAfterLastDot(classPath));
                        }
                    }
                }
                continue;
            }

            String newName;
            if (!imp.isStatic() && imp.isAsterisk()) {
                // import a.b.*; → 全体をパッケージとして扱う
                newName = store.getOrCreate(name, NameKind.PACKAGE);
            } else if (imp.isStatic()) {
                String classPath = imp.isAsterisk() ? name : headBeforeLastDot(name);
                String member = imp.isAsterisk() ? null : tailAfterLastDot(name);
                newName = renameClassPath(classPath, renameMap);
                if (member != null) {
                    if (JavaDenylist.contains(member)) {
                        newName = newName + "." + member;
                    } else {
                        String newMember = store.getOrCreate(member, NameKind.METHOD);
                        renameMap.putIfAbsent(member, newMember);
                        newName = newName + "." + newMember;
                    }
                }
            } else {
                // import a.b.C;
                newName = renameClassPath(name, renameMap);
            }
            imp.setName(newName);
        }
        return libraryImportedNames;
    }

    /** a.b.C 形式のパスをパッケージ部とクラス部(最終セグメント)に分けて匿名化する。 */
    private String renameClassPath(String classPath, Map<String, String> renameMap) {
        String pkg = headBeforeLastDot(classPath);
        String cls = tailAfterLastDot(classPath);

        String newCls;
        if (JavaDenylist.contains(cls)) {
            newCls = cls;
        } else {
            newCls = store.getOrCreate(cls, NameKind.CLASS);
            renameMap.putIfAbsent(cls, newCls);
        }
        if (pkg == null) {
            return newCls;
        }
        String newPkg = store.getOrCreate(pkg, NameKind.PACKAGE);
        return newPkg + "." + newCls;
    }

    private static String headBeforeLastDot(String s) {
        int i = s.lastIndexOf('.');
        return i < 0 ? null : s.substring(0, i);
    }

    private static String tailAfterLastDot(String s) {
        int i = s.lastIndexOf('.');
        return i < 0 ? s : s.substring(i + 1);
    }

    private void maskStringLiterals(CompilationUnit cu) {
        cu.findAll(StringLiteralExpr.class).forEach(lit -> {
            String replacement = store.getOrCreate(lit.getValue(), NameKind.STRING);
            lit.setValue(replacement);
        });
        // テキストブロック("""..."""、Java 15+)は通常の文字列リテラルとは別ノードなので個別に処理する
        cu.findAll(TextBlockLiteralExpr.class).forEach(lit -> {
            String replacement = store.getOrCreate(lit.getValue(), NameKind.STRING);
            lit.setValue(replacement);
        });
    }

    private void detectAnnotationStringWarnings(CompilationUnit cu, List<String> warnings) {
        cu.findAll(NormalAnnotationExpr.class).forEach(ann -> {
            for (MemberValuePair pair : ann.getPairs()) {
                if (pair.getValue() instanceof StringLiteralExpr) {
                    warnings.add("アノテーション @" + ann.getNameAsString()
                            + " 内に文字列リテラルを検出しました。手動確認してください。");
                    break;
                }
            }
        });
        cu.findAll(SingleMemberAnnotationExpr.class).forEach(ann -> {
            if (ann.getMemberValue() instanceof StringLiteralExpr) {
                warnings.add("アノテーション @" + ann.getNameAsString()
                        + " 内に文字列リテラルを検出しました。手動確認してください。");
            }
        });
    }

    private void removeComments(CompilationUnit cu) {
        List<Comment> comments = new ArrayList<>(cu.getAllContainedComments());
        for (Comment c : comments) {
            c.remove();
        }
        cu.getComment().ifPresent(Node::remove);
    }
}
