package com.arangodb.codegen;

import com.squareup.javapoet.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Set;


@SupportedAnnotationTypes("com.arangodb.codegen.GenerateSyncApi")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GenerateSyncApiProcessor extends AbstractProcessor {
    TypeElement syncParentClient;
    TypeElement syncParentClientImpl;

    private Element getElementAnnotatedWith(RoundEnvironment roundEnv, Class<? extends Annotation> annotation) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
        if (elements.isEmpty()) {
            throw new RuntimeException("No class annotated with @" + annotation.getSimpleName() + " found!");
        }
        if (elements.size() > 1) {
            throw new RuntimeException("More than one class annotated with @" + annotation.getSimpleName() + " found!");
        }
        return elements.iterator().next();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (syncParentClient == null) {
            syncParentClient = (TypeElement) getElementAnnotatedWith(roundEnv, SyncClientParent.class);
        }

        if (syncParentClientImpl == null) {
            syncParentClientImpl = (TypeElement) getElementAnnotatedWith(roundEnv, SyncClientParentImpl.class);
        }

        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "found @GenerateSyncApi at " + element);
                if (!element.getKind().isInterface()) {
                    throw new IllegalArgumentException("@GenerateSyncApi can only be applied to interfaces!");
                }

                TypeSpec interfaceSpec = createInterface(element).build();
                JavaFile interfaceJavaFile = JavaFile.builder(getPackageName(element), interfaceSpec)
                        .build();

                TypeSpec implSpec = createImplementation(element, interfaceSpec);
                JavaFile implJavaFile = JavaFile.builder(getPackageName(element), implSpec)
                        .build();

                try {
                    interfaceJavaFile.writeTo(processingEnv.getFiler());
                    implJavaFile.writeTo(processingEnv.getFiler());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        }

        return true;
    }

    private static String getPackageName(Element e) {
        while (e.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
            e = e.getEnclosingElement();
        }
        return ((PackageElement) e.getEnclosingElement()).getQualifiedName().toString();
    }

    private TypeSpec.Builder createInterface(Element e) {
        return TypeSpec
                .interfaceBuilder(e.getSimpleName() + "SyncX")
                .addSuperinterface(ParameterizedTypeName.get(
                        ClassName.get(syncParentClient),
                        ClassName.get(getPackageName(e), e.getSimpleName().toString())))
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Synchronous version of {@link $T}", e);
    }

    private TypeSpec createImplementation(Element e, TypeSpec interfaceSpec) {
        String packageName = getPackageName(e);
        String className = e.getSimpleName().toString();

        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(packageName, className), "delegate", Modifier.FINAL)
                .addStatement("super(delegate)")
                .build();

        return TypeSpec
                .classBuilder(className + "SyncXImpl")
                .addSuperinterface(ClassName.get(packageName, interfaceSpec.name))
                .superclass(
                        ParameterizedTypeName.get(
                                ClassName.get(syncParentClientImpl),
                                ClassName.get(packageName, className)
                        )
                )
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(constructor)
                .build();
    }

}
