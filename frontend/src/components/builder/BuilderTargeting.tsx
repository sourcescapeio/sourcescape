import React from 'react';

import {
  AnchorButton,
  Tooltip,
  Tag,
  Spinner,
  Text,
  Popover,
  Card,
  Button,
  Menu,
  MenuItem
} from '@blueprintjs/core';

export function BuilderTargetingComponent(props: {
  targeting: any,
  loading: boolean,
  fileFilter: string | null,
  setTargeting: (_: any) => void,
  setFileFilter: (_: string | null) => void,
}){
  const { repo, branch, sha, index } = props.targeting;

  return <>
    {
      // <pre>{JSON.stringify(this.props.targeting)}</pre>
      // targeting indexing
    }
    {index && 
      (index.work && <AnchorButton minimal><Spinner size={15} /></AnchorButton>)
    }
    {/* {this.props.targeting.index && 
      (this.props.targeting.index.indexId && <pre>{this.props.targeting.index.indexId}</pre>)
    }       */}
    {props.targeting.all && <AnchorButton minimal={true} icon="asterisk"/>}
    {props.loading && <Spinner size={15} />}
    {repo && <Popover 
      minimal 
      position="bottom-right"
      autoFocus={false}
    >
      <AnchorButton
        key="repo"
        minimal={true}
        icon="git-repo"
        text={repo.repo}
      />
      <Card>
        <div>
          {repo.branch && <Tag intent="primary" interactive onClick={() => (props.setTargeting({repoId: repo.repoId, branch: repo.branch}))}>{repo.branch}</Tag>}
          {repo.sha && <Tag style={{marginLeft: 10}} interactive onClick={() => (props.setTargeting({repoId: repo.repoId, sha: repo.sha}))}>{repo.sha.substring(0, 9)} {repo.dirty && "*"}</Tag>}
        </div>
        {repo.shaMessage && <div style={{marginTop: 6, maxWidth: 500}}><Text ellipsize>{repo.shaMessage}</Text></div>}
        <div style={{marginTop: 6}}>
          <Tag rightIcon="cross" intent="danger" minimal interactive onClick={() => (props.setTargeting({}))}>Clear</Tag>
        </div>
      </Card>
    </Popover> }
    {branch && <Popover
      minimal 
      position="bottom-right"
    >
      <AnchorButton 
        key="branch"
        minimal={true} 
        icon="git-branch" 
        text={branch.branch}
      />
      <Card>
        <div>
          {branch.sha && <Tag interactive onClick={() => (props.setTargeting({repoId: repo.repoId, sha: branch.sha}))}>{branch.sha.substring(0, 9)}</Tag>}
        </div>
        {branch.shaMessage && <div style={{marginTop: 6, maxWidth: 500}}><Text ellipsize>{branch.shaMessage}</Text></div>}
        <div style={{marginTop: 6}}>
          <Tag rightIcon="cross" intent="danger" minimal interactive onClick={() => (props.setTargeting({repoId: repo.repoId}))}>Clear</Tag>
        </div>
      </Card>
    </Popover>}
    {sha && <Popover
      minimal 
      position="bottom-right"
    >
      <AnchorButton 
        key="branch"
        minimal={true}
        icon="git-branch" 
        text={sha.sha.substring(0, 6)} 
      />
      <Card>
        {sha.shaMessage && <div><Text ellipsize>{sha.shaMessage}</Text></div>}
        <div style={{marginTop: 6}}>
          <Tag rightIcon="cross" intent="danger" minimal interactive onClick={() => (props.setTargeting({repoId: repo.repoId}))}>Clear</Tag>
        </div>
      </Card>
    </Popover>}
    {props.fileFilter && <Popover
      minimal
      position="bottom-right"
    >
      <AnchorButton 
        key="folder"
        minimal={true} 
        icon="folder-open" 
        text={props.fileFilter}
      />
      <Card>
      <Button icon="cross" intent="danger" minimal text="Clear" onClick={() => (props.setFileFilter(null))}/>
      </Card>
      </Popover>
    }
  </>
}