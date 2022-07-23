import React from 'react';
import { connect } from 'react-redux';
import styled from 'styled-components';
import classNames from "classnames";
import { Link } from 'react-router-dom';
import { 
  Icon, 
  Classes,
  Button, 
  Dialog,
  MenuItem, 
  Menu, 
  MenuDivider,
  Popover,
  Navbar,
  Spinner,
  Switch,
} from '@blueprintjs/core';

import Container from 'react-bootstrap/Container';
import Row from 'react-bootstrap/Row';
import Col from 'react-bootstrap/Col';

// import { mapStateToProps, mapDispatchToProps, mergeProps } from 'store';
import { Avatar, getInitials } from './Avatar';

function ProjectDropdownComponentBase(props: {
  loading: boolean,
  disabled: boolean,
  debug: boolean
}) {
  // const { allOrgs, currentOrg, email, isSuper, disabled } = this.props;
  const { loading, disabled } = props;
  const currentOrg = { name: null };
  // const email = "jieren@lychee.ai"
  const orgName = "Blarg Char";

  // const orgName = (currentOrg || {}).name;
  const orgInitials = getInitials(orgName, " ");
  // const emailInitials = getInitials(email, "@");

  return <Popover 
    minimal={true}
    autoFocus={false}
    position="bottom-left"
    disabled={disabled}    
  >
    <Button style={{padding: 5}} minimal={true} disabled={disabled}>
      { props.loading ? <Spinner /> : <Avatar
        initials={orgInitials}
        label={orgName}
        color="#29A634"
      />}
    </Button>
    <Menu>
      <Avatar
        initials={orgInitials}
        label={orgName}
        subLabel="114 repos"
        color="#29A634"
      />
      <MenuItem 
        text="Org settings"
        href="/org-settings"
      />
      <MenuItem
        text="Switch org"
      >
        {/* {allOrgs.map((item: any) => {
          const isSelected = (item.id === (currentOrg || {}).id);
          return <MenuItem
            labelElement={isSelected ? <Icon icon="small-tick" /> : null}
            disabled={isSelected}
            icon="folder-close"
            key={item.id}
            onClick={() => {
              // this.props.setOrg(item.id);
            }}
            text={item.name}
          />
        })} */}
        <MenuDivider />
        <MenuItem 
          icon="new-object" 
          text="Create org" 
          key="new"
          href="/create-org"
        />
      </MenuItem>
      <MenuDivider />
      {/* <Switch checked={this.props.debug} onChange={(e) => (this.props.setDebug(e.target.checked))} label="Debug"/> */}
      <MenuItem
        text="Global settings"
      />
    </Menu>
  </Popover>
}

const ProjectDropdownComponent = ProjectDropdownComponentBase;
// connect(
//   mapStateToProps,
//   mapDispatchToProps,
//   mergeProps,
// )(ProjectDropdownComponentBase);

export {
  ProjectDropdownComponent
}
