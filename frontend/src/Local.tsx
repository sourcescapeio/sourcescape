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
import { Loading } from 'components/shared/Loading';
import { LocalViewWrapper } from 'components/dash';
import { LocalOnboardingContainer } from 'components/user/Onboarding';
// import { Card, H3 } from '@blueprintjs/core';

/**
  * Websocket
  */
let protocol;
if (window.location.protocol === "https:") {
  protocol = "wss";
} else {
  protocol = "ws";
}

// const enqueueItem = enqueue(localStore.dispatch);

const ws = new Sockette(`${protocol}://${window.location.host}/api/socket`, {
  maxAttempts: 10,
  onopen: (e: any) => console.log('Connected!', e),
  onmessage: (e: any) => {
    const eData = JSON.parse(e.data);
    // even enqueue pings to clean up
    console.warn(eData);
    // enqueueItem(eData);
  },
  onreconnect: (e: any) => console.log('Reconnecting...', e),
  onmaximum: (e: any) => console.log('Stop Attempting!', e),
  onclose: (e: any) => console.log('Closed!', e),
  onerror: (e: any) => console.log('Error:', e)
});

function LocalAppBase() {
  return <Router>
    <Routes>
      <Route path="/onboarding" element={<LocalOnboardingContainer />} />
      <Route path="*" element={<div>Not Found</div>} />
      <Route path="/" element={<InitialRedirectComponent />} />
      {
        // Wrapped
      }
      <Route path="/" element={<LocalViewWrapper debug={false} currentPath="/"/>}>

        {/* <Route path="/loading" element={<Loading loading={true}/>} /> */}
        <Route path="/console" element={<div>test</div>} />
      </Route>
    </Routes>
  </Router>
}

export default LocalAppBase