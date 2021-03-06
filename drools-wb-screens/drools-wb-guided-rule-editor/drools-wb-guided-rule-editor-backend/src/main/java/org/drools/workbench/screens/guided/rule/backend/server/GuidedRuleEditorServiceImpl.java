/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.guided.rule.backend.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;

import org.drools.workbench.models.commons.backend.rule.RuleModelDRLPersistenceImpl;
import org.drools.workbench.models.commons.backend.rule.RuleModelIActionPersistenceExtension;
import org.drools.workbench.models.datamodel.imports.Import;
import org.drools.workbench.models.datamodel.oracle.PackageDataModelOracle;
import org.drools.workbench.models.datamodel.rule.RuleModel;
import org.drools.workbench.screens.guided.rule.model.GuidedEditorContent;
import org.drools.workbench.screens.guided.rule.service.GuidedRuleEditorService;
import org.drools.workbench.screens.guided.rule.type.GuidedRuleDRLResourceTypeDefinition;
import org.drools.workbench.screens.guided.rule.type.GuidedRuleDSLRResourceTypeDefinition;
import org.guvnor.common.services.backend.config.SafeSessionInfo;
import org.guvnor.common.services.backend.exceptions.ExceptionUtilities;
import org.guvnor.common.services.backend.util.CommentedOptionFactory;
import org.guvnor.common.services.backend.validation.GenericValidator;
import org.guvnor.common.services.project.model.Package;
import org.guvnor.common.services.shared.metadata.model.Metadata;
import org.guvnor.common.services.shared.metadata.model.Overview;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.jboss.errai.bus.server.annotations.Service;
import org.kie.workbench.common.services.backend.service.KieService;
import org.kie.workbench.common.services.datamodel.backend.server.DataModelOracleUtilities;
import org.kie.workbench.common.services.datamodel.backend.server.service.DataModelService;
import org.kie.workbench.common.services.datamodel.model.PackageDataModelOracleBaselinePayload;
import org.uberfire.backend.server.util.Paths;
import org.uberfire.backend.vfs.Path;
import org.uberfire.ext.editor.commons.service.CopyService;
import org.uberfire.ext.editor.commons.service.DeleteService;
import org.uberfire.ext.editor.commons.service.RenameService;
import org.uberfire.io.IOService;
import org.uberfire.java.nio.file.FileAlreadyExistsException;
import org.uberfire.rpc.SessionInfo;
import org.uberfire.workbench.events.ResourceOpenedEvent;

@Service
@ApplicationScoped
public class GuidedRuleEditorServiceImpl
        extends KieService<GuidedEditorContent>
        implements GuidedRuleEditorService {

    @Inject
    @Named("ioStrategy")
    private IOService ioService;

    @Inject
    private CopyService copyService;

    @Inject
    private DeleteService deleteService;

    @Inject
    private RenameService renameService;

    @Inject
    private Event<ResourceOpenedEvent> resourceOpenedEvent;

    @Inject
    private DataModelService dataModelService;

    @Inject
    private GuidedRuleEditorServiceUtilities utilities;

    @Inject
    private GuidedRuleDRLResourceTypeDefinition drlResourceType;

    @Inject
    private GuidedRuleDSLRResourceTypeDefinition dslrResourceType;

    @Inject
    private GenericValidator genericValidator;

    @Inject
    private CommentedOptionFactory commentedOptionFactory;

    private SafeSessionInfo safeSessionInfo;

    private Collection<RuleModelIActionPersistenceExtension> persistenceExtensions = new ArrayList<>();

    public GuidedRuleEditorServiceImpl() {
    }

    @Inject
    public GuidedRuleEditorServiceImpl(final SessionInfo sessionInfo,
                                       final Instance<RuleModelIActionPersistenceExtension> persistenceExtensionInstance) {
        this.safeSessionInfo = new SafeSessionInfo(sessionInfo);

        persistenceExtensionInstance.forEach(persistenceExtensions::add);
    }

    @Override
    public Path create(final Path context,
                       final String fileName,
                       final RuleModel model,
                       final String comment) {
        try {
            final Package pkg = projectService.resolvePackage(context);
            final String packageName = (pkg == null ? null : pkg.getPackageName());
            model.setPackageName(packageName);

            // Temporal fix for https://bugzilla.redhat.com/show_bug.cgi?id=998922
            model.getImports().addImport(new Import("java.lang.Number"));

            final org.uberfire.java.nio.file.Path nioPath = Paths.convert(context).resolve(fileName);
            final Path newPath = Paths.convert(nioPath);

            if (ioService.exists(nioPath)) {
                throw new FileAlreadyExistsException(nioPath.toString());
            }

            ioService.write(nioPath,
                            toSource(newPath,
                                     model),
                            commentedOptionFactory.makeCommentedOption(comment));

            return newPath;
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    @Override
    public RuleModel load(final Path path) {
        try {
            final String drl = ioService.readAllString(Paths.convert(path));
            final List<String> globals = utilities.loadGlobalsForPackage(path);
            final PackageDataModelOracle oracle = dataModelService.getDataModel(path);

            RuleModel ruleModel = null;
            if (dslrResourceType.accept(path)) {
                final String[] dsls = utilities.loadDslsForPackage(path);
                ruleModel = RuleModelDRLPersistenceImpl.getInstance().unmarshalUsingDSL(drl,
                                                                                        globals,
                                                                                        oracle,
                                                                                        persistenceExtensions,
                                                                                        dsls);
            } else {
                ruleModel = RuleModelDRLPersistenceImpl.getInstance().unmarshal(drl,
                                                                                globals,
                                                                                oracle,
                                                                                persistenceExtensions);
            }

            return ruleModel;
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    @Override
    public GuidedEditorContent loadContent(final Path path) {
        return super.loadContent(path);
    }

    @Override
    protected GuidedEditorContent constructContent(Path path,
                                                   Overview overview) {
        final RuleModel model = load(path);
        final PackageDataModelOracle oracle = dataModelService.getDataModel(path);
        final PackageDataModelOracleBaselinePayload dataModel = new PackageDataModelOracleBaselinePayload();

        //Get FQCN's used by model
        final GuidedRuleModelVisitor visitor = new GuidedRuleModelVisitor(model);
        final Set<String> consumedFQCNs = visitor.getConsumedModelClasses();

        //Get FQCN's used by Globals
        consumedFQCNs.addAll(oracle.getPackageGlobals().values());

        DataModelOracleUtilities.populateDataModel(oracle,
                                                   dataModel,
                                                   consumedFQCNs);

        //Signal opening to interested parties
        resourceOpenedEvent.fire(new ResourceOpenedEvent(path,
                                                         safeSessionInfo));

        return new GuidedEditorContent(model,
                                       overview,
                                       dataModel);
    }

    @Override
    public Path save(final Path resource,
                     final RuleModel model,
                     final Metadata metadata,
                     final String comment) {
        try {
            final Package pkg = projectService.resolvePackage(resource);
            final String packageName = (pkg == null ? null : pkg.getPackageName());
            model.setPackageName(packageName);

            Metadata currentMetadata = metadataService.getMetadata(resource);
            ioService.write(Paths.convert(resource),
                            toSourceUnexpanded(resource,
                                               model),
                            metadataService.setUpAttributes(resource,
                                                            metadata),
                            commentedOptionFactory.makeCommentedOption(comment));

            fireMetadataSocialEvents(resource,
                                     currentMetadata,
                                     metadata);
            return resource;
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    @Override
    public void delete(final Path path,
                       final String comment) {
        try {
            deleteService.delete(path,
                                 comment);
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    @Override
    public Path rename(final Path path,
                       final String newName,
                       final String comment) {
        try {
            return renameService.rename(path,
                                        newName,
                                        comment);
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    @Override
    public Path copy(final Path path,
                     final String newName,
                     final String comment) {
        try {
            return copyService.copy(path,
                                    newName,
                                    comment);
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    @Override
    public Path copy(final Path path,
                     final String newName,
                     final Path targetDirectory,
                     final String comment) {
        try {
            return copyService.copy(path,
                                    newName,
                                    targetDirectory,
                                    comment);
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    @Override
    public String toSource(final Path path,
                           final RuleModel model) {
        try {
            return toSourceExpanded(path,
                                    model);
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    @Override
    public List<ValidationMessage> validate(final Path path,
                                            final RuleModel content) {
        try {
            final String source = toSourceUnexpanded(path,
                                                     content);
            return genericValidator.validate(path,
                                             source);
        } catch (Exception e) {
            throw ExceptionUtilities.handleException(e);
        }
    }

    private String toSourceExpanded(final Path path,
                                    final RuleModel model) {
        //This returns the expanded Source as used in "View Source" within the UI.
        return sourceServices.getServiceFor(Paths.convert(path)).getSource(Paths.convert(path),
                                                                           model);
    }

    private String toSourceUnexpanded(final Path path,
                                      final RuleModel content) {
        //Wrap RuleModel as we need to control whether the DSLs are expanded. Both DRL and DSLR files should not have
        //DSLs expanded. In the case of DSLRs we need to explicitly control escaping plain-DRL to prevent attempts
        //by drools to expand it, by forcing the Model->DRL persistence into believing the model has DSLs.
        final RuleModelWrapper model = new RuleModelWrapper(content,
                                                            dslrResourceType.accept(path));
        final String source = RuleModelDRLPersistenceImpl.getInstance().marshal(model,
                                                                                persistenceExtensions);
        return source;
    }
}
