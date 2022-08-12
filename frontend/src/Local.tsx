import React from 'react';
import { connect } from 'react-redux';
// import { mapStateToProps, mapDispatchToProps, mergeProps, initialRefresh } from 'store';

import {
  BrowserRouter as Router,
  Route,
  Routes
} from 'react-router-dom';
import { Provider } from 'react-redux';
// import { localStore, enqueue } from 'store';
import Sockette from 'sockette';

// import { history } from 'history';

// import { LocalViewWrapper } from 'components/dash/local';
// import { Row, Col } from 'react-bootstrap';

// // these should be containers
// import { SrcLogCompilerContainer } from 'containers/debug/SrcLogCompiler';
// import { GraphConsoleComponent } from 'components/debug/GraphConsole';
// import { RelationalConsoleComponent } from 'components/debug/RelationalConsole';

// import { CreateOrgComponent } from 'components/user/CreateOrg';
import { InitialRedirectComponent } from 'components/user/InitialRedirect';
// import { LocalOrgSettingsComponent } from 'components/settings/LocalOrgSettings';

// Testing
// import { CompilerDataComponent } from 'components/debug/CompilerData';
// import { TestIndexingComponent } from 'components/debug/TestIndexing';
// import { TestParsingComponent } from 'components/debug/TestParsing';

// import { LogsListingComponent } from 'components/logs/LogsListing';
// import { LogDetailsComponent } from 'components/logs/LogDetails';
// import { LogItemComponent } from 'components/logs/LogItem';

import qs from 'qs';

// containers
// import { LocalQueryBuilderContainer } from 'containers/builder';
// import { LocalCollectionConfigContainer } from 'containers/indexing';
// import { LocalCacheManagementContainer } from 'containers/caching';
// import { BranchVisualizationContainer } from 'containers/branching';
// import { LocalOnboardingContainer } from 'containers/onboarding';
// import { 
//   SavedQueryListingContainer,
//   SavedQueryDetailsContainer,
// } from 'containers/saved';

// styles
import '@blueprintjs/core/lib/css/blueprint.css';
import '@blueprintjs/icons/lib/css/blueprint-icons.css';
// import '@blueprintjs/select/lib/css/blueprint-select.css';
import 'bootstrap/dist/css/bootstrap-grid.min.css';
// import 'draft-js/dist/Draft.css';
// import 'prismjs/themes/prism-twilight.css';
import { LocalViewWrapper } from 'components/dash';
import { LocalOnboardingContainer } from 'components/user/Onboarding';
import { SrcLogConsoleContainer } from 'components/console/SrcLogConsole';
import { RelationalConsoleContainer } from 'components/console/RelationalConsole';
import { GraphConsoleContainer } from 'components/console/GraphConsole';

import { GrammarProvider } from 'contexts/GrammarContext';

function LocalAppBase() {
  return <GrammarProvider>
    <Router>
      <Routes>
        <Route path="/onboarding" element={<LocalOnboardingContainer />} />
        <Route path="*" element={<div>Not Found</div>} />
        <Route path="/" element={<InitialRedirectComponent />} />
        {
          // Wrapped
        }
        <Route path="/" element={<LocalViewWrapper debug={false} />}>
          <Route path="/console" element={<div>Test</div>} />
          <Route path="/srclog-console" element={<SrcLogConsoleContainer />} />
          <Route path="/relational-console" element={<RelationalConsoleContainer />} />
          <Route path="/graph-console" element={<GraphConsoleContainer />} />
        </Route>
      </Routes>
    </Router>
  </GrammarProvider>
}

export default LocalAppBase
