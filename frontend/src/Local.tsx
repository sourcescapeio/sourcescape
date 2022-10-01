import {
  BrowserRouter as Router,
  Route,
  Routes
} from 'react-router-dom';

import { InitialRedirectComponent } from 'components/user/InitialRedirect';

// styles
import '@blueprintjs/core/lib/css/blueprint.css';
import '@blueprintjs/icons/lib/css/blueprint-icons.css';
import 'bootstrap/dist/css/bootstrap-grid.min.css';
import { LocalViewWrapper } from 'components/dash';
import { LocalOnboardingContainer } from 'components/user/Onboarding';
import { SrcLogCompilerContainer } from 'components/console/SrcLogCompiler';
import { RelationalConsoleContainer } from 'components/console/RelationalConsole';
import { GraphConsoleContainer } from 'components/console/GraphConsole';
import { SrcLogConsoleContainer } from 'components/console/SrcLogConsole';

import { GrammarProvider } from 'contexts/GrammarContext';
import { QueryBuilderContainer } from 'components/builder/QueryBuilder';
import { TestIndexingContainer } from 'components/debug/TestIndexing';

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
          <Route path="/console" element={<QueryBuilderContainer />} />
          <Route path="/srclog-console" element={<SrcLogConsoleContainer />} />
          <Route path="/srclog-compiler" element={<SrcLogCompilerContainer />} />
          <Route path="/relational-console" element={<RelationalConsoleContainer />} />
          <Route path="/graph-console" element={<GraphConsoleContainer />} />
          {
            // debug
          }
          <Route path="/test-indexing" element={<TestIndexingContainer />} />
          {/* <Route path="/test-parsing" element={<TestParsingContainer />} /> */}
        </Route>
      </Routes>
    </Router>
  </GrammarProvider>
}

export default LocalAppBase
