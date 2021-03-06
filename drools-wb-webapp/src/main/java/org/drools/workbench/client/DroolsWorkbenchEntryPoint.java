/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.drools.workbench.client;

import javax.inject.Inject;

import com.google.gwt.user.client.Window;
import org.drools.workbench.client.resources.i18n.AppConstants;
import org.guvnor.common.services.shared.config.AppConfigService;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.ioc.client.api.EntryPoint;
import org.kie.workbench.common.workbench.client.admin.DefaultAdminPageHelper;
import org.kie.workbench.common.workbench.client.admin.resources.i18n.PreferencesConstants;
import org.kie.workbench.common.workbench.client.entrypoint.DefaultWorkbenchEntryPoint;
import org.kie.workbench.common.workbench.client.menu.DefaultWorkbenchFeaturesMenusHelper;
import org.uberfire.client.mvp.AbstractWorkbenchPerspectiveActivity;
import org.uberfire.client.mvp.ActivityBeansCache;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.workbench.widgets.menu.WorkbenchMenuBarPresenter;
import org.uberfire.ext.preferences.client.admin.page.AdminPage;
import org.uberfire.mvp.impl.DefaultPlaceRequest;
import org.uberfire.workbench.model.menu.MenuFactory;
import org.uberfire.workbench.model.menu.Menus;

@EntryPoint
public class DroolsWorkbenchEntryPoint extends DefaultWorkbenchEntryPoint {

    protected AppConstants constants = AppConstants.INSTANCE;

    protected PlaceManager placeManager;

    protected DefaultWorkbenchFeaturesMenusHelper menusHelper;

    protected WorkbenchMenuBarPresenter menuBar;

    protected AdminPage adminPage;

    protected DefaultAdminPageHelper adminPageHelper;

    @Inject
    public DroolsWorkbenchEntryPoint(final Caller<AppConfigService> appConfigService,
                                     final ActivityBeansCache activityBeansCache,
                                     final PlaceManager placeManager,
                                     final DefaultWorkbenchFeaturesMenusHelper menusHelper,
                                     final WorkbenchMenuBarPresenter menuBar,
                                     final AdminPage adminPage,
                                     final DefaultAdminPageHelper adminPageHelper) {
        super(appConfigService,
              activityBeansCache);
        this.placeManager = placeManager;
        this.menusHelper = menusHelper;
        this.menuBar = menuBar;
        this.adminPage = adminPage;
        this.adminPageHelper = adminPageHelper;
    }

    @Override
    public void setupMenu() {
        adminPage.addScreen("root",
                            AppConstants.INSTANCE.Settings());
        adminPage.setDefaultScreen("root");

        adminPage.addPreference("root",
                                "LibraryPreferences",
                                AppConstants.INSTANCE.Library(),
                                "fa-cubes",
                                "preferences");

        adminPage.addPreference("root",
                                "ArtifactRepositoryPreference",
                                AppConstants.INSTANCE.ArtifactRepository(),
                                "fa-archive",
                                "preferences");

        final AbstractWorkbenchPerspectiveActivity defaultPerspective = menusHelper.getDefaultPerspectiveActivity();

        menusHelper.addRolesMenuItems();
        menusHelper.addUtilitiesMenuItems();

        final Menus menus = MenuFactory
                .newTopLevelMenu(constants.Home())
                .respondsWith(() -> {
                    if (defaultPerspective != null) {
                        placeManager.goTo(new DefaultPlaceRequest(defaultPerspective.getIdentifier()));
                    } else {
                        Window.alert("Default perspective not found.");
                    }
                })
                .endMenu()
                .newTopLevelMenu(constants.Perspectives())
                .withItems(menusHelper.getPerspectivesMenuItems())
                .endMenu()
                .build();

        menuBar.addMenus(menus);
    }
}
