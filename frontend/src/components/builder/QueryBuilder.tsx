import {
  Callout,
  Card,
  Dialog,
  Button,
  Icon,
  Drawer,
} from '@blueprintjs/core';

import { Loading } from 'components/shared/Loading';

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';

import Hotkeys from 'react-hot-keys';
import { BuilderConsoleComponent } from './BuilderConsole';
import React, { useState } from 'react';
import { BuilderTargetingComponent } from './BuilderTargeting';
import axios from 'axios';

function QueryBuilderComponent(props: {
  error: any,
  loading: boolean,
  // sub
  console: React.ReactNode
}) {

  return <Hotkeys
    keyName="command+s,command+shift+s,command+i,command+shift+p,command+o"
    // onKeyDown={this.onKeyDown}
  >
    {
      //loading
    }
    { props.error && <Callout intent="danger">
      {JSON.stringify(props.error, null, 2)}
      </Callout>
    }
    {
      //dialogs
    }
    {/* <Drawer 
      icon="download"
      title="Indexing"
      isOpen={this.state.indexingPanel} 
      onClose={this.toggleIndexingPanel} 
      size={500}>
      <IndexingPanelComponent queue={this.props.queue} />
    </Drawer> */}
    {/* <QueryBarComponent
      isOpen={this.state.openBar}
      onClose={this.toggleBar}
      targeting={this.props.targeting}
      activeRepos={this.props.activeRepos}
      skippedRepos={this.props.skippedRepos}
      allQueries={this.props.allQueries}
      allTargeting={this.props.allTargeting}
      shas={this.props.allSHAs}
      languages={this.props.languages}
      // actions
      selectLanguage={this.props.selectLanguage}
      setQuery={this.props.setQuery}
      setTargeting={this.props.setTargeting}
      setFileFilter={this.props.setFileFilter}
    />
    <SchemaBuilderComponent
      isOpen={this.state.schemaModal}
      onClose={this.toggleSchemaModal}
      context={this.props.context}
      fileFilter={this.props.fileFilter}
      indexIds={this.props.indexIds}
    /> */}
    {
      // basic dialogs
    }
    {/* <Dialog isOpen={this.state.saveTargetingModal} title="Save targeting as..." onClose={this.toggleSaveTargetingModal}>
      <SaveNameComponent 
        save={this.props.saveTargeting}
        context={this.props.context}
        headers={results.headers}
        close={this.toggleSaveTargetingModal}
      />
    </Dialog>       */}
    {/* <Dialog isOpen={this.state.saveQueryModal} title="Save query as..." onClose={this.toggleSaveQueryModal}>
      <SaveNameComponent 
        save={this.props.saveQuery}
        context={this.props.context}
        headers={results.headers}
        close={this.toggleSaveQueryModal}
      />
    </Dialog> */}
    {/* <Dialog isOpen={this.state.debugMenu} title="Debug" onClose={this.toggleDebugMenu}>
      <BuilderDebugComponent
        current={this.props.context.srclog}
        postOperation={this.props.postOperation}
        onClose={this.toggleDebugMenu}
        //
        trace={this.props.trace}
        toggleTrace={this.props.toggleTrace}
        debug={this.state.debug}
        toggleDebug={this.toggleDebug}
        //
        disableSrcLog={this.props.demo}
      />
    </Dialog> */}
    {
      // legacy
    }
    {/* <Dialog isOpen={this.state.reportsModal} style={{width: 'unset', minWidth: 500, maxHeight: '80vh'}} title="Build report" onClose={this.toggleReportsModal}>
      <ReportsModalComponent 
        data={results.data}
        results={this.props.allResults}
        context={this.props.context}
        targeting={this.props.targeting}
        fileFilter={this.props.fileFilter}
        indexIds={this.props.indexIds}
      />
    </Dialog> */}
    {
      //main
    }
    <Container>
      <Row>
        <Col>
          <Loading loading={props.loading}/>
          {props.console}
          {/* <BuilderConsoleComponent 
            postOperation={this.props.postOperation}
            context={this.props.context}
            trace={this.props.trace}
            debug={this.state.debug}
            refreshQuery={this.props.refreshQuery}
            emptyContext={emptyContext}
            //
            isIndexing={this.props.isIndexing}
            toggleIndexingPanel={this.toggleIndexingPanel}
            // Bar stuff
            toggleDebug={this.toggleDebugMenu}
            toggleReportsModal={this.toggleReportsModal}
            toggleBar={this.toggleBar}
            toggleSaveQueryModal={this.toggleSaveQueryModal}
            toggleSaveTargetingModal={this.toggleSaveTargetingModal}
            reset={this.props.reset}
            // targeting
            fileFilter={this.props.fileFilter}
            targeting={this.props.targeting}
            targetingLoading={this.props.targetingLoading}
            setTargeting={this.props.setTargeting}
            setFileFilter={this.props.setFileFilter}
            // query cache
            savedQuery={this.props.savedQuery}
            cache={this.props.cache}
            latestCacheUpdate={this.props.latestCacheUpdate}
            //
            select={this.select}
            // mediate by trace
            selected={selectedSet}
            // hover
            setHovered={this.setHovered}
            hovered={this.state.hovered}
            // inject grammar
            languages={this.props.languages}
            selectedLanguage={this.props.selectedLanguage}
            selectLanguage={this.props.selectLanguage}
            //setLanguages
            grammar={this.props.grammar}
            incomplete={this.props.incomplete}
            autocomplete={this.props.autocomplete}
            defaultPayloads={this.props.defaultPayloads}
          /> */}
        </Col>
      </Row>
      <Row>
        <Col>
          {/* {emptyContext && (this.props.children || <div></div>) }
          {!emptyContext && 
            <Card style={{overflow: 'scroll'}}>
              {
                this.props.trace ? 
                  <BuilderTraceComponent 
                    loading={results.loading}
                    error={this.props.error}
                    data={results.data}
                    columns={results.columns}
                    //
                    sizeEstimate={results.sizeEstimate}
                    progress={results.progress}
                    time={results.time}
                    debug={this.props.debug}
                    // selected = trace selected
                  />: <BuilderResultsComponent
                    loading={results.loading}
                    error={this.props.error}
                    data={results.data}
                    columns={results.columns}
                    //caching
                    loadingCachedResults={this.props.loadingCachedResults}
                    createCache={this.props.createCache}
                    cache={this.props.cache}
                    latestCacheUpdate={this.props.latestCacheUpdate}
                    savedQuery={this.props.savedQuery}
                    page={this.props.page}
                    setPage={this.props.setPage}
                    //schema
                    createSchema={this.toggleSchemaModal}
                    //
                    sizeEstimate={results.sizeEstimate}
                    progress={results.progress}
                    time={results.time}
                    debug={this.state.debug}
                    //
                    openItem={this.props.openItem}
                    //
                    nodeParents={this.props.context.parents}
                    nodeChildren={this.props.context.children}
                    select={this.select}
                    selected={this.props.context.selected}
                    //
                    setHovered={this.setHovered}
                    hovered={this.state.hovered}
                  />
              }
            </Card>
          } */}
        </Col>
      </Row>
    </Container>
  </Hotkeys>
}

const DEFAULT_LANGUAGE = 'javascript';

const DEFAULT_CONTEXT = {
  state: {
    language: DEFAULT_LANGUAGE,
    nodes: [],
    edges: [],
    aliases: {},
  },
  display: "",
  highlights: [],
  srclog: "",
  aliases: {},
  //
  referenceMap: {},
  //
  parents: [],
  children: [],
  //
  selected: null,
  traceSelected: {},
};

export function QueryBuilderContainer() {
  const [hovered, setHovered] = useState(false);
  const [dragging, setDragging] = useState(false);

  /**
   * Targeting
   */
  // const fetchTargeting = async () => {
  //   const resp = await axios({
  //     method: 'GET',
  //     url: '/api/orgs/-1/saved-targeting'
  //   });

  //   resp.data
  // }


  return <QueryBuilderComponent
    error={null}
    loading={false}
    console={
      <BuilderConsoleComponent
        context={DEFAULT_CONTEXT}
        hovered={hovered}
        setHovered={setHovered}
        setDragging={setDragging}
        // sub
        // toolbar
        // targeting
        targeting={
          <BuilderTargetingComponent
            targeting={{}}
            loading={false}
            fileFilter={null}
            setTargeting={() => (null)}
            setFileFilter={() => (null)}      
          />
        }
        /* <BuilderTargetingComponent 
          loading={this.props.targetingLoading}
          targeting={this.props.targeting}
          setTargeting={this.props.setTargeting}
          setFileFilter={this.props.setFileFilter}
          fileFilter={this.props.fileFilter}
          toggleBar={this.props.toggleBar}
        /> */
      />
    }


    // // loading
    // loading={this.state.loading || this.props.loadingGrammars}
    // error={this.state.error}
    // // grammar
    // languages={Object.keys(this.props.grammars || {})}
    // selectedLanguage={language}
    // selectLanguage={this.selectLanguage}
    // grammar={(this.props.grammars || {})[language]}
    // incomplete={(this.props.incompletes || {})[language]}
    // defaultPayloads={(this.props.defaultPayloads || {})[language]}
    // autocomplete={(this.props.autocompletes || {})[language]}
    // // data
    // context={this.state.context}
    // results={results}
    // allResults={this.state.results} // ???
    // // caching
    // loadingCachedResults={this.state.loadingCachedResults}
    // createCache={this.createCache}
    // cache={this.state.cache}
    // latestCacheUpdate={this.state.latestCacheUpdate}
    // savedQuery={this.state.savedQuery}
    // page={this.state.page}
    // setPage={this.setPage}
    // // snapshots
    // // targeting
    // setTargeting={this.setTargeting}
    // setFileFilter={this.setFileFilter}
    // setQuery={this.setQuery}
    // fileFilter={this.state.fileFilter}
    // targeting={this.state.resolvedTargeting}
    // targetingLoading={this.state.targetingLoading}
    // indexIds={this.getIndexIds()}
    // // command bar injection
    // activeRepos={this.props.activeRepos}
    // skippedRepos={this.props.skippedRepos}
    // allQueries={this.state.allQueries}
    // allSHAs={this.state.allSHAs}
    // allTargeting={this.state.allTargeting}
    // //settings
    // demo={this.props.demo}
    // trace={this.state.trace}
    // toggleTrace={this.toggleTrace}
    // // indexing
    // queue={this.props.queue}
    // isIndexing={this.props.isIndexing}
    // // actions
    // openItem={this.openItem}
    // postOperation={this.postOperation}
    // saveQuery={this.saveQuery}
    // saveTargeting={this.saveTargeting}
    // refreshQuery={this.refreshQuery}
    // reset={this.reset}
  />
}
