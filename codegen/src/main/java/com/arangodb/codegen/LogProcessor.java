package com.arangodb.codegen;

import com.squareup.javapoet.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("com.arangodb.codegen.Log")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class LogProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        try {
            writeSourceFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "found @Log at " + element);
                element.getEnclosedElements().forEach(member -> {

                    if (member.getKind() == ElementKind.METHOD
                            && member.getModifiers().contains(Modifier.PUBLIC)
                            && !member.getModifiers().contains(Modifier.STATIC)
                    ) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "--> " + element.getSimpleName() + "." + member.getSimpleName());
                        ExecutableElement executableMember = (ExecutableElement) member;
                        List<ParameterSpec> params = executableMember.getParameters().stream().map(ParameterSpec::get).collect(Collectors.toList());
                        String paramNames = executableMember.getParameters().stream()
                                .map(VariableElement::getSimpleName)
                                .map(CharSequence::toString)
                                .collect(Collectors.joining(", "));

                        TypeMirror typeMirror = executableMember.getReturnType();
                        TypeMirror returnType = ((DeclaredType) typeMirror).getTypeArguments().get(0);

                        MethodSpec methodSpec = MethodSpec
                                .methodBuilder(executableMember.getSimpleName().toString())
                                .addParameters(params)
                                .addModifiers(executableMember.getModifiers())
                                .addCode("delegate." + executableMember.getSimpleName() + "(" + paramNames + ")")
                                .returns(TypeName.get(returnType))
                                .build();

                        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "## " + methodSpec.toString());
                    }
                });
            }
        }

        return true;
    }

    private void writeSourceFile() throws IOException {
        MethodSpec main = MethodSpec.methodBuilder("main")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addParameter(String[].class, "args")
                .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!")
                .build();

        TypeSpec helloWorld = TypeSpec.classBuilder("HelloWorld")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(main)
                .build();

        JavaFile javaFile = JavaFile.builder("com.example.helloworld", helloWorld)
                .build();

        javaFile.writeTo(processingEnv.getFiler());
    }


}
