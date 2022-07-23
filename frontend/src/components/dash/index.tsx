import React from 'react';

import { connect } from 'react-redux';

import {
  Spinner,
  H1,
  Breadcrumbs,
  Breadcrumb,
} from '@blueprintjs/core';

import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';
import Container from 'react-bootstrap/Container';

import { SidebarComponent } from './Sidebar';

// import { mapStateToProps, mapDispatchToProps, mergeProps } from 'store';
import { Loading } from 'components/shared/Loading';

import {
  WrapperRoot,
  MainContainer,
  SidebarContainer,
  ContentContainer,
  DashboardContainer,
} from './Layout';
import { Outlet } from 'react-router';

// adapter between url and actual components


function LocalViewWrapperBase(props: {
  currentPath: string,
  debug: boolean
}) {
  //    this.props.fetchProfile();    

  // profileLoading
  // setDebug
  const profileLoading = false;
  const { currentPath, debug,  } = props;

  return (
    <WrapperRoot>
      <DashboardContainer>
        <SidebarContainer>
          <SidebarComponent
            debug={debug}
            // setDebug={setDebug}
            loading={profileLoading}
            currentPath={currentPath}
          />
        </SidebarContainer>
        <MainContainer>
          <Loading loading={profileLoading} />
          <ContentContainer className="bp3-dark-app-background-color">
            <Container>
              <Outlet/>
            </Container>
          </ContentContainer>
        </MainContainer>
      </DashboardContainer>
    </WrapperRoot>
  );  
}

const LocalViewWrapper = LocalViewWrapperBase;

// const LocalViewWrapper = connect(
//   mapStateToProps,
//   mapDispatchToProps,
//   mergeProps,
// )(LocalViewWrapperBase);

export {
  LocalViewWrapper,
};
