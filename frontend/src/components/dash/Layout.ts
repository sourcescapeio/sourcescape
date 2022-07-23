import styled from 'styled-components';

const WrapperRoot = styled.div`
  flex-direction: column;
  display: flex;
  flex-grow: 1;
  min-width: 1024px;
  .MuiDrawer-paper-2 {
    z-index: 10;
  }  
`;

const DashboardContainer = styled.div`
  display: flex;
  min-height: 100vh;
`;

const SidebarContainer = styled.div`
  width: 200px;
  overflow-y: hidden;
  height: 100vh;
`;

const MainContainer = styled.main`
  z-index: 0;
  flex: 1;
  height: 100vh;
  overflow-y: scroll;
`;

const ContentContainer = styled.div`
  min-width: 760px;
  flex-grow: 1;
  padding-top: 20px;
  height: 100%;
  overflow-y: scroll;  
  background-color: #293742;
`;

export { 
  DashboardContainer,
  ContentContainer,
  MainContainer,
  SidebarContainer,
  WrapperRoot,
};
