import React from 'react';
import styled from 'styled-components';
import { 
  Card,
  Popover,
} from '@blueprintjs/core';

import Container from 'react-bootstrap/Container';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

import { ProjectDropdownComponent } from './ProjectDropdown';
import { SidebarLink, SidebarItem, SidebarMenu, SidebarHeader } from './SidebarLayout';

function SidebarComponent(props: {
  loading: boolean,
  debug: boolean,
  currentPath: string,
}) {
  return (
    <Card style={{marginTop: 0, height: '100%'}}>
      <SidebarHeader>
        <ProjectDropdownComponent 
          loading={props.loading}
          debug={props.debug}
          // setDebug={props.setDebug}
          disabled={true}
        />
      </SidebarHeader>
      <SidebarMenu>
        {/* <SidebarItem
          linkTo="/console"
          currentPath={props.currentPath}
          label="Console"
          icon="console"
        /> */}
        <SidebarItem
          linkTo="/srclog-console"
          currentPath={props.currentPath}
          label="SrcLog"
          icon="console"
        />
        <SidebarItem
          linkTo="/srclog-compiler"
          currentPath={props.currentPath}
          label="Compiler"
          icon="layout-hierarchy"
        />
        <SidebarItem
          linkTo="/relational-console"
          currentPath={props.currentPath}
          label="Relational"
          icon="left-join"
        />
        <SidebarItem
          linkTo="/graph-console"
          currentPath={props.currentPath}
          label="Graph"
          icon="graph"
        />
      </SidebarMenu>
      { props.debug && <SidebarMenu>
        <SidebarItem
          linkTo="/indexing"
          currentPath={props.currentPath}
          label="Indexing"
          icon="projects"
        />
        <SidebarItem
          linkTo="/test-indexing"
          currentPath={props.currentPath}
          label="Test Indexing"
          icon="download"
        />
        <SidebarItem
          linkTo="/test-parsing"
          currentPath={props.currentPath}
          label="Test Parsing"
          icon="eye-open"
        />
        <SidebarItem
          linkTo="/caching"
          currentPath={props.currentPath}
          label="Caching"
          icon="heat-grid"
        />
        <SidebarItem
          linkTo="/logs"
          currentPath={props.currentPath}
          label="Logs"
          icon="align-left"
        />
      </SidebarMenu> }
      <div style={{position: 'absolute', bottom: 20}}>
        <SidebarLink
          href="https://sourcescape.io"
          label={<p style={{fontFamily: 'Source Sans Pro', fontSize: 20, paddingTop: 5}}>
            /<i><b>source</b></i> /<i><b>scape</b></i>
          </p>}
        />
      </div>
    </Card>
  );
}

export {
  SidebarComponent,
};
