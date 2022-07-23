import React, { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import classNames from "classnames";

import { 
  Icon, 
  Classes,
  AnchorButton,
  IconName,
} from '@blueprintjs/core';

function SidebarMenu (props: {children: any}) {
  return <ul style={{margin: '20px 0', padding: '0px'}}>{props.children}</ul>
}

function SidebarHeader (props: {children: any}) {
  return (
    <div style={{display: 'flex', flexDirection: 'row'}}>{props.children}</div>
  );
}

function SidebarLink(props: {
  icon?: IconName,
  label: any,
  href: string
}) {
  const { icon, label, href, ...rest } = props;
  return (
    <li style={{listStyle: 'none'}}>
      <AnchorButton 
        minimal 
        icon={icon}
        href={href || "#"}
        disabled={!href}
        {...rest}
      >
        {label}
      </AnchorButton>
    </li>
  );
}

function SidebarItem(props: {
  linkTo: string,
  icon: IconName,
  label: string,
  currentPath: any,
  disabled?: boolean,
  hidden?: boolean
}) {

  const { linkTo, icon, label, currentPath, disabled, hidden } = props;

  const matchLink = (currentPath === linkTo);
  const subMatch = currentPath.startsWith(linkTo + "/")
  const active = (matchLink || subMatch);

  const disabledStyles: CSSProperties = disabled ? {pointerEvents: 'none'} : {};
  const activeStyles = active ? {color: 'white', fontWeight: 'bold'} : {color: '#999'};

  return (
    <li style={{listStyle: 'none', display: (hidden ? 'none' : '')}}>
      <Link
        to={linkTo}
        style={{
          ...disabledStyles,
          ...activeStyles,
        }}
        className={
          classNames(
            Classes.BUTTON, 
            Classes.MINIMAL, 
            disabled ? Classes.DISABLED : null
          )
        }
      >
        <Icon icon={icon} />
        <span style={{textAlign: 'left'}} >{label}</span>
      </Link>
    </li>
  );
};

export {
  SidebarItem,
  SidebarLink,
  SidebarMenu,
  SidebarHeader,
};
